package com.example.foodapp.service;

import com.example.foodapp.model.Payment;
import com.example.foodapp.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PaymentService {
    private final PaymentRepository repo;

    public PaymentService(PaymentRepository repo) { this.repo = repo; }

    public Payment start(Long orderId, BigDecimal amount, String currency, String provider){
        Payment p = new Payment();
        p.setOrderId(orderId);
        p.setAmount(amount);
        p.setCurrency(currency);
        p.setProvider(provider);
        p.setStatus("INITIATED");
        return repo.save(p);
    }

    public Payment attachProviderPaymentId(Long paymentId, String providerPaymentId){
        Payment p = repo.findById(paymentId).orElseThrow();
        p.setProviderPaymentId(providerPaymentId);
        return repo.save(p);
    }

    public Payment findByProviderPaymentId(String providerPaymentId){
        return repo.findByProviderPaymentId(providerPaymentId).orElse(null);
    }

    public Payment markSucceeded(Long paymentId, String txnId, String provider){
        Payment p = repo.findById(paymentId).orElseThrow();
        p.setProvider(provider);
        p.setTxnId(txnId);
        p.setStatus("SUCCEEDED");
        return repo.save(p);
    }

    public Payment markSucceededByProviderPaymentId(String providerPaymentId, String captureId, String provider){
        var p = repo.findByProviderPaymentId(providerPaymentId).orElseThrow();
        p.setProvider(provider);
        p.setTxnId(captureId);
        p.setStatus("SUCCEEDED");
        return repo.save(p);
    }

    public void markCodPending(Long orderId){
        // create or update a payment row for COD
        Payment p = findLatestByOrderId(orderId);
        if (p == null) {
            p = new Payment();
            p.setOrderId(orderId);
        }
        p.setProvider("COD");
        p.setStatus("PENDING");
        repo.save(p);
    }

    public Payment findLatestByOrderId(Long orderId){
        return repo.findTopByOrderIdOrderByIdDesc(orderId);
    }

    public Payment findById(Long id){ return repo.findById(id).orElse(null); }
}