// src/main/java/com/example/foodapp/service/StripeService.java
package com.example.foodapp.service;

import com.example.foodapp.model.Order;
import com.example.foodapp.repository.OrderRepository;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
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

    public record StartOut(boolean ok, String checkoutUrl, Long paymentId) {}

    @Value("${stripe.secret.key}")
    private String secretKey;

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    @Value("${stripe.webhook.secret:}")
    private String webhookSecret;

    public StripeService(OrderService orderService,
                         PaymentService paymentService,
                         OrderRepository orderRepository) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.orderRepository = orderRepository;
    }

    public StartOut createCheckoutSession(Long orderId){
        try{
            Stripe.apiKey = secretKey;

            Order order = orderService.findById(orderId);
            if (order == null) return new StartOut(false, null, null);

            // -------- IMPORTANT: use remainingToPay (after discount + gift card) --------
            BigDecimal amountToCharge = order.getRemainingToPay();
            if (amountToCharge == null || amountToCharge.compareTo(BigDecimal.ZERO) <= 0) {
                // fallback if no gift card or something weird
                amountToCharge = order.getGrandTotal();
            }
            if (amountToCharge == null) {
                amountToCharge = BigDecimal.ZERO;
            }
            amountToCharge = amountToCharge.setScale(2, RoundingMode.HALF_UP);
            long amountCents = amountToCharge.movePointRight(2).longValueExact();
            // ---------------------------------------------------------------------------

            // internal Payment row uses the same final amount
            var payment = paymentService.start(orderId, amountToCharge, "USD", "STRIPE");

            SessionCreateParams params =
                    SessionCreateParams.builder()
                            .setMode(SessionCreateParams.Mode.PAYMENT)
                            .setSuccessUrl(appUrl + "/payment/stripe/return?orderId="+orderId+"&session_id={CHECKOUT_SESSION_ID}")
                            .setCancelUrl(appUrl + "/orders")
                            .addLineItem(
                                    SessionCreateParams.LineItem.builder()
                                            .setQuantity(1L)
                                            .setPriceData(
                                                    SessionCreateParams.LineItem.PriceData.builder()
                                                            .setCurrency("usd")
                                                            .setUnitAmount(amountCents)
                                                            .setProductData(
                                                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                            .setName("Order #" + orderId)
                                                                            .build()
                                                            ).build()
                                            ).build()
                            ).build();

            Session session = Session.create(params);
            paymentService.attachProviderPaymentId(payment.getId(), session.getId());

            return new StartOut(true, session.getUrl(), payment.getId());
        }catch(Exception e){
            return new StartOut(false, null, null);
        }
    }

    public boolean verifySession(String sessionId){
        try{
            Stripe.apiKey = secretKey;
            Session s = Session.retrieve(sessionId);
            return "complete".equalsIgnoreCase(s.getStatus());
        }catch(Exception e){
            return false;
        }
    }

    /** Create a PaymentIntent that Apple Pay / Google Pay can confirm on the client */
    public Map<String, Object> createWalletIntent(Long orderId) {
        Stripe.apiKey = secretKey; // IMPORTANT: set API key here as well

        Order o = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        // -------- again: use remainingToPay here --------
        BigDecimal amountToCharge = o.getRemainingToPay();
        if (amountToCharge == null || amountToCharge.compareTo(BigDecimal.ZERO) <= 0) {
            amountToCharge = o.getGrandTotal();
        }
        if (amountToCharge == null) {
            amountToCharge = BigDecimal.ZERO;
        }
        amountToCharge = amountToCharge.setScale(2, RoundingMode.HALF_UP);
        long amountInCents = amountToCharge.movePointRight(2).longValueExact();
        // ------------------------------------------------

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency("usd")
                .setDescription("SpiceJar Order #" + o.getId())
                .putMetadata("orderId", String.valueOf(o.getId()))
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
            return out;
        } catch (StripeException e) {
            throw new RuntimeException("Stripe error", e);
        }
    }

    /** Verify webhook and mark orders paid when PI succeeds */
    public void handleStripeWebhook(String payload, String sigHeader) {
        try {
            // Always verify signature → no Gson dependency needed
            Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

            switch (event.getType()) {
                case "payment_intent.succeeded" -> {
                    PaymentIntent pi = (PaymentIntent) event.getDataObjectDeserializer()
                            .getObject().orElse(null);
                    if (pi != null) {
                        String orderId = pi.getMetadata().get("orderId");
                        if (orderId != null) {
                            orderService.markPaid(Long.valueOf(orderId));
                        }
                    }
                }
                // optionally handle other events
                default -> {}
            }

        } catch (SignatureVerificationException e) {
            // bad signature
            throw new RuntimeException("Invalid webhook signature", e);
        } catch (Exception e) {
            throw new RuntimeException("Invalid webhook", e);
        }
    }
}
