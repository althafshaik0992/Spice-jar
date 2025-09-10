// src/main/java/com/example/foodapp/service/PaymentService.java
package com.example.foodapp.service;

import com.example.foodapp.model.Payment;
import com.example.foodapp.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PaymentService {
    private final PaymentRepository repo;

    public PaymentService(PaymentRepository repo) {
        this.repo = repo;
    }

    /** create INITIATED row */
    public Payment start(Long orderId, BigDecimal amount, String currency, String provider){
        Payment p = new Payment();
        p.setOrderId(orderId);
        p.setAmount(amount);
        p.setCurrency(currency);
        p.setProvider(provider); // STRIPE / PAYPAL / COD
        p.setStatus("INITIATED");
        return repo.save(p);
    }

    /** store provider order id (PaymentIntent id / PayPal order id) */
    public Payment attachProviderPaymentId(Long paymentId, String providerPaymentId){
        Payment p = repo.findById(paymentId).orElseThrow();
        p.setProviderPaymentId(providerPaymentId);
        return repo.save(p);
    }

    public Payment markSucceeded(Long paymentId, String txnId, String provider){
        Payment p = repo.findById(paymentId).orElseThrow();
        p.setProvider(provider);
        p.setTxnId(txnId);
        p.setStatus("SUCCEEDED");
        return repo.save(p);
    }

    public Payment markSucceededByProviderPaymentId(String providerPaymentId, String captureId, String provider){
        Payment p = repo.findByProviderPaymentId(providerPaymentId).orElseThrow();
        p.setProvider(provider);
        p.setTxnId(captureId);
        p.setStatus("SUCCEEDED");
        return repo.save(p);
    }

    public void markPendingCod(Long paymentId){
        Payment p = repo.findById(paymentId).orElseThrow();
        p.setProvider("COD");
        p.setStatus("PENDING");
        repo.save(p);
    }

    public Payment findById(Long id){ return repo.findById(id).orElse(null); }
    public Payment findLatestByOrderId(Long orderId){ return repo.findTopByOrderIdOrderByIdDesc(orderId); }
}
