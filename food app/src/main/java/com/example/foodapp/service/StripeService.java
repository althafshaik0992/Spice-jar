package com.example.foodapp.service;

import com.example.foodapp.model.Payment;
import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class StripeService {

    @Value("${stripe.secret.key}")
    private String secretKey;

    @Value("${stripe.publishable.key}")
    private String publishableKey;

    public Map<String,String> createPaymentIntent(Payment p){
        try{
            Stripe.apiKey = secretKey;
            long amountCents = p.getAmount().movePointRight(2).longValueExact();

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountCents)
                    .setCurrency("usd")
                    .addPaymentMethodType("card")
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);
            Map<String,String> out = new HashMap<>();
            out.put("clientSecret", intent.getClientSecret());
            out.put("publishableKey", publishableKey);
            // store external id if you want:
            // p.setExternalRef(intent.getId()); paymentRepository.save(p);
            return out;
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }
}
