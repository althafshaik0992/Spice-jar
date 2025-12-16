// src/main/java/com/example/foodapp/service/StripeService.java
package com.example.foodapp.service;

import com.example.foodapp.model.Order;
import com.example.foodapp.model.Payment;
import com.example.foodapp.repository.OrderRepository;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionRetrieveParams;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Service
public class StripeService {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final OrderRepository orderRepository;

    @Value("${stripe.secret.key}")
    private String secretKey;

    @Value("${stripe.webhook.secret:}")
    private String webhookSecret;

    public StripeService(OrderService orderService,
                         PaymentService paymentService,
                         OrderRepository orderRepository) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.orderRepository = orderRepository;
    }

    /**
     * PaymentIntent flow (card form + Apple/Google Pay)
     * Returns: clientSecret, paymentIntentId, paymentId (DB)
     */
    public Map<String, Object> createWalletIntent(Long orderId) {
        Stripe.apiKey = secretKey;

        Order o = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        BigDecimal amountToCharge = o.getRemainingToPay();
        if (amountToCharge == null || amountToCharge.compareTo(BigDecimal.ZERO) <= 0) {
            amountToCharge = o.getGrandTotal();
        }
        if (amountToCharge == null) amountToCharge = BigDecimal.ZERO;

        amountToCharge = amountToCharge.setScale(2, RoundingMode.HALF_UP);
        long amountInCents = amountToCharge.movePointRight(2).longValueExact();

        // ✅ Create DB payment row first (INITIATED)
        Payment payment = paymentService.start(o, amountToCharge, "USD", "STRIPE");

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency("usd")
                .setDescription("SpiceJar Order #" + o.getId())
                .putMetadata("orderId", String.valueOf(o.getId()))
                .putMetadata("paymentId", String.valueOf(payment.getId()))
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build()
                )
                .build();

        try {
            PaymentIntent pi = PaymentIntent.create(params);

            Map<String, Object> out = new HashMap<>();
            out.put("clientSecret", pi.getClientSecret());
            out.put("paymentIntentId", pi.getId());
            out.put("paymentId", payment.getId());
            return out;

        } catch (StripeException e) {
            throw new RuntimeException("Stripe error", e);
        }
    }

    public boolean isPaymentIntentSucceeded(String paymentIntentId) {
        try {
            Stripe.apiKey = secretKey;
            PaymentIntent pi = PaymentIntent.retrieve(paymentIntentId);
            return "succeeded".equalsIgnoreCase(pi.getStatus());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * ✅ FIX: Used by OrderController return flow when DB payment is still INITIATED.
     * This method checks Stripe and, if succeeded, updates your DB payment -> SUCCEEDED.
     */
    @Transactional
    public void syncPaymentIfSucceeded(Payment payment) {
        if (payment == null) return;

        // only for Stripe payments
        if (payment.getProvider() == null || !"STRIPE".equalsIgnoreCase(payment.getProvider())) return;

        // We need a PI id stored in transactionId (best) OR providerPaymentId (fallback)
        String piId = payment.getTransactionId();
        if (piId == null || piId.isBlank()) {
            piId = payment.getProviderPaymentId();
        }

        if (piId == null || !piId.startsWith("pi_")) {
            return; // nothing to sync
        }

        try {
            Stripe.apiKey = secretKey;
            PaymentIntent pi = PaymentIntent.retrieve(piId);

            if ("succeeded".equalsIgnoreCase(pi.getStatus())) {
                // IMPORTANT: this method must exist in PaymentService
                paymentService.markSucceededByPaymentId(
                        payment.getId(),
                        pi.getId(),   // providerPaymentId
                        pi.getId(),   // transactionId
                        "STRIPE"
                );
            }
        } catch (Exception e) {
            e.printStackTrace(); // don’t crash return flow
        }
    }

    public void handleStripeWebhook(String payload, String sigHeader) {
        try {
            Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

            if ("payment_intent.succeeded".equals(event.getType())) {
                PaymentIntent pi = (PaymentIntent) event.getDataObjectDeserializer()
                        .getObject().orElse(null);

                if (pi != null) {
                    String orderId = pi.getMetadata().get("orderId");
                    String paymentId = pi.getMetadata().get("paymentId");

                    if (orderId != null && paymentId != null) {
                        orderService.markPaid(Long.valueOf(orderId));
                        paymentService.markSucceededByPaymentId(
                                Long.valueOf(paymentId),
                                pi.getId(),
                                pi.getId(),
                                "STRIPE"
                        );
                    }
                }
            }

        } catch (SignatureVerificationException e) {
            throw new RuntimeException("Invalid webhook signature", e);
        } catch (Exception e) {
            throw new RuntimeException("Invalid webhook", e);
        }
    }

    // Checkout session verify (legacy)
    public boolean verifySession(String sessionId) {
        try {
            Stripe.apiKey = secretKey;
            Session session = Session.retrieve(
                    sessionId,
                    SessionRetrieveParams.builder().addExpand("payment_intent").build(),
                    null
            );
            return "complete".equalsIgnoreCase(session.getStatus())
                    || "paid".equalsIgnoreCase(session.getPaymentStatus());
        } catch (Exception e) {
            return false;
        }
    }

    public String getPaymentIntentIdFromSession(String sessionId) {
        try {
            Stripe.apiKey = secretKey;
            Session s = Session.retrieve(sessionId);
            return s.getPaymentIntent();
        } catch (Exception e) {
            return null;
        }
    }

    public String refundStripePayment(String transactionId, BigDecimal amount) throws StripeException {
        Stripe.apiKey = secretKey;

        long cents = amount.movePointRight(2).longValue();

        RefundCreateParams.Builder b = RefundCreateParams.builder().setAmount(cents);

        if (transactionId != null && transactionId.startsWith("pi_")) {
            b.setPaymentIntent(transactionId);
        } else if (transactionId != null && transactionId.startsWith("ch_")) {
            b.setCharge(transactionId);
        } else {
            throw new IllegalArgumentException("Stripe transactionId must be pi_... or ch_..., got: " + transactionId);
        }

        Refund refund = Refund.create(b.build());
        return refund.getId();
    }
}
