// src/main/java/com/example/foodapp/service/PaymentService.java
package com.example.foodapp.service;

import com.example.foodapp.model.Order;
import com.example.foodapp.model.Payment;
import com.example.foodapp.model.PaymentMethod;
import com.example.foodapp.repository.PaymentRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository repo;
    private final EmailService emailService;
    private final PaymentMethodService paymentMethodService;

    public PaymentService(PaymentRepository repo,
                          EmailService emailService,
                          PaymentMethodService paymentMethodService) {
        this.repo = repo;
        this.emailService = emailService;
        this.paymentMethodService = paymentMethodService;
    }

    public Payment findByProviderPaymentId(String providerPaymentId) {
        return repo.findByProviderPaymentId(providerPaymentId).orElse(null);
    }

    public Payment findById(Long id) {
        return repo.findById(id).orElse(null);
    }

    public Payment findLatestByOrderId(Long orderId) {
        return repo.findTopByOrder_IdAndRefundFalseOrderByIdDesc(orderId).orElse(null);
    }

    public Payment findLatestSuccessfulCharge(Long orderId) {
        return repo.findTopByOrder_IdAndRefundFalseAndStatusOrderByIdDesc(orderId, "SUCCEEDED").orElse(null);
    }

    public List<Payment> findRefundsForOrder(Long orderId) {
        return repo.findByOrder_IdAndRefundTrueOrderByIdDesc(orderId);
    }

    @Transactional
    public Payment start(Order order, BigDecimal amount, String currency, String provider) {
        Payment p = new Payment();
        p.setOrder(order);
        p.setAmount(amount);
        p.setCurrency(currency != null ? currency : "USD");
        p.setProvider(provider);
        p.setStatus("INITIATED");
        p.setRefund(false);
        p.setCreatedAt(LocalDateTime.now());

        PaymentMethod pm = paymentMethodService.findByCode(provider);
        if (pm != null) {
            p.setPaymentMethod(pm);
            p.setCode(pm.getCode());
            p.setDisplayName(pm.getDisplayName());
        } else {
            p.setCode(provider);
            p.setDisplayName(provider);
        }

        // IMPORTANT: start with nulls to avoid unique collisions
        p.setProviderPaymentId(null);
        p.setTransactionId(null);

        return repo.save(p);
    }

    @Transactional
    public Payment attachProviderPaymentId(Long paymentId, String providerPaymentId) {
        Payment p = repo.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        p.setProviderPaymentId(providerPaymentId);
        return repo.save(p);
    }

    /**
     * Legacy: mark succeeded by providerPaymentId
     */
    @Transactional
    public void markSucceededByProviderPaymentId(String providerPaymentId,
                                                 String transactionId,
                                                 String provider) {
        Payment p = repo.findByProviderPaymentId(providerPaymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for providerPaymentId: " + providerPaymentId));

        p.setStatus("SUCCEEDED");
        p.setProvider(provider);
        p.setTransactionId(transactionId);
        repo.save(p);
    }

    /**
     * ✅ FIX: mark succeeded by paymentId (used for PaymentIntent flow)
     * Also stores providerPaymentId=pi_... so you can trace it later.
     */
    @Transactional
    public void markSucceededByPaymentId(Long paymentId,
                                         String providerPaymentId,
                                         String transactionId,
                                         String provider) {

        Payment p = repo.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        p.setStatus("SUCCEEDED");
        p.setProvider(provider);

        // store pi_... here so refunds work
        p.setProviderPaymentId(providerPaymentId);
        p.setTransactionId(transactionId);

        repo.save(p);

        log.info("Payment SUCCEEDED: paymentId={}, provider={}, providerPaymentId={}, transactionId={}",
                paymentId, provider, providerPaymentId, transactionId);
    }

    @Transactional
    public Payment createRefundRecord(Order order,
                                      Payment originalCharge,
                                      BigDecimal amount,
                                      String reason) {

        Payment refund = new Payment();
        refund.setOrder(order);
        refund.setAmount(amount);
        refund.setCurrency(originalCharge.getCurrency());
        refund.setProvider(originalCharge.getProvider());
        refund.setStatus("REFUND_INITIATED");

        refund.setPaymentMethod(originalCharge.getPaymentMethod());
        refund.setCode(originalCharge.getCode());
        refund.setDisplayName(originalCharge.getDisplayName());

        // REFUND AGAINST the original transaction id (pi_... or PayPal captureId)
        refund.setTransactionId(originalCharge.getTransactionId());

        // Never set providerPaymentId on refunds (avoids unique constraint duplicates)
        refund.setProviderPaymentId(null);

        refund.setRefund(true);
        refund.setRefundReason(reason);
        refund.setNotes(reason);
        refund.setCreatedAt(LocalDateTime.now());

        return repo.save(refund);
    }

    @Transactional
    public void markRefundCompleted(Payment refund, String externalRefundId, Order order) {
        refund.setRefundExternalId(externalRefundId);
        refund.setRefundedAt(LocalDateTime.now());
        refund.setStatus("REFUNDED");
        repo.save(refund);

        if (order != null) {
            emailService.sendRefundConfirmation(order, refund);
        }
    }

    @Transactional
    public void markRefundFailed(Payment refund, String message) {
        refund.setStatus("REFUND_FAILED");
        refund.setNotes(message);
        repo.save(refund);
    }


    /// admin helpers

    public AdminRefundView getAdminRefundView(Long orderId) {
        Payment original = findLatestSuccessfulCharge(orderId);
        if (original == null) {
            // fallback: show latest attempt even if status not SUCCEEDED
            original = findLatestByOrderId(orderId);
        }

        List<Payment> refunds = findRefundsForOrder(orderId);
        return new AdminRefundView(original, refunds);
    }

    public record AdminRefundView(Payment originalPayment, List<Payment> refundRows) {}





    public List<Payment> recentPayments(int limit) {
        // You want successful charges only (NOT refunds)
        // Example: status = SUCCEEDED and refundExternalId is null
        return repo.findRecentSuccessfulCharges(limit);
    }

    public List<Payment> recentRefunds(int limit) {
        // Refund rows only
        // Example: type = REFUND or refundExternalId != null
        return repo.findRecentRefunds(limit);
    }


}
