package com.example.foodapp.service;

import com.example.foodapp.model.Order;
import com.example.foodapp.model.Payment;
import com.example.foodapp.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PaymentService {

    private final PaymentRepository repo;
    private final OrderService orderService;

    public PaymentService(PaymentRepository repo,
                          OrderService orderService) {
        this.repo = repo;
        this.orderService = orderService;
    }

    /**
     * Decide what amount we actually charge for this order.
     * Priority:
     *  1) order.remainingToPay (after discount + gift card)
     *  2) order.grandTotal
     *  3) amount parameter
     */
    private BigDecimal resolveChargeAmount(Long orderId, BigDecimal amountParam) {
        BigDecimal result = null;

        if (orderId != null) {
            Order order = orderService.findById(orderId);
            if (order != null) {
                // after discount + gift card
                BigDecimal remaining = order.getRemainingToPay();
                if (remaining != null && remaining.compareTo(BigDecimal.ZERO) > 0) {
                    result = remaining;
                } else if (order.getGrandTotal() != null) {
                    // fallback – e.g. no gift card applied yet
                    result = order.getGrandTotal();
                }
            }
        }

        if (result == null) {
            result = (amountParam != null) ? amountParam : BigDecimal.ZERO;
        }

        return result.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Create a new payment record. The amount that gets persisted is
     * always the resolved charge amount (aware of gift card + discount).
     */
    public Payment start(Long orderId, BigDecimal amount, String currency, String provider) {
        BigDecimal chargeAmount = resolveChargeAmount(orderId, amount);

        Payment p = new Payment();
        p.setOrderId(orderId);
        p.setAmount(chargeAmount);
        p.setCurrency(currency);
        p.setProvider(provider);
        p.setStatus("INITIATED");
        return repo.save(p);
    }

    public Payment attachProviderPaymentId(Long paymentId, String providerPaymentId) {
        Payment p = repo.findById(paymentId).orElseThrow();
        p.setProviderPaymentId(providerPaymentId);
        return repo.save(p);
    }

    public Payment findByProviderPaymentId(String providerPaymentId) {
        return repo.findByProviderPaymentId(providerPaymentId).orElse(null);
    }

    public Payment markSucceeded(Long paymentId, String txnId, String provider) {
        Payment p = repo.findById(paymentId).orElseThrow();
        p.setProvider(provider);
        p.setTxnId(txnId);
        p.setStatus("SUCCEEDED");
        return repo.save(p);
    }

    public Payment markSucceededByProviderPaymentId(String providerPaymentId,
                                                    String captureId,
                                                    String provider) {
        Payment p = repo.findByProviderPaymentId(providerPaymentId).orElseThrow();
        p.setProvider(provider);
        p.setTxnId(captureId);
        p.setStatus("SUCCEEDED");
        return repo.save(p);
    }

    public void markCodPending(Long orderId) {
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

    public Payment findLatestByOrderId(Long orderId) {
        return repo.findTopByOrderIdOrderByIdDesc(orderId);
    }

    public Payment findById(Long id) {
        return repo.findById(id).orElse(null);
    }
}
