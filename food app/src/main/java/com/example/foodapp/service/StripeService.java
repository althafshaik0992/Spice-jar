package com.example.foodapp.service;

import com.example.foodapp.model.Order;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StripeService {

    private final OrderService orderService;
    private final PaymentService paymentService;

    public record StartOut(boolean ok, String checkoutUrl, Long paymentId) {}

    @Value("${stripe.secret.key}")
    private String secretKey;

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    public StripeService(OrderService orderService, PaymentService paymentService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    public StartOut createCheckoutSession(Long orderId){
        try{
            Stripe.apiKey = secretKey;

            Order order = orderService.findById(orderId);
            if (order == null) return new StartOut(false, null, null);

            long amountCents = order.getGrandTotal().movePointRight(2).longValueExact();

            var payment = paymentService.start(orderId, order.getGrandTotal(), "USD", "STRIPE");

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
            // store provider id if you want: session.getId()
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
}