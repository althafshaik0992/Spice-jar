// src/main/java/com/example/foodapp/model/Payment.java
package com.example.foodapp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
public class Payment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ single source of truth for order_id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 8)
    private String currency;   // "USD"

    @Column(nullable = false, length = 32)
    private String status;     // INITIATED, SUCCEEDED, FAILED, REFUND_INITIATED, REFUNDED, REFUND_FAILED

    @Column(nullable = false, length = 20)
    private String provider;   // STRIPE, PAYPAL, COD

    // ✅ For charge flows: Stripe sessionId OR PayPal orderId (keep UNIQUE for charges)
    @Column(name = "provider_payment_id", length = 80, unique = true)
    private String providerPaymentId;

    // ✅ For charge flows: Stripe paymentIntentId OR PayPal captureId
    @Column(name = "transaction_id", length = 80)
    private String transactionId;

    // ✅ For refunds: Stripe refund id OR PayPal refund id
    @Column(name = "refund_external_id", length = 80)
    private String refundExternalId;

    @Column(name = "refund_reason", length = 255)
    private String refundReason;

    @Column(nullable = false)
    private Boolean refund = false;

    private LocalDateTime refundedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Optional display fields (don’t mark NOT NULL in DB)
    private String code;
    private String displayName;

    @Column(length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethod paymentMethod;

    // Convenience (helps your older code)
    @Transient
    public Long getOrderId() {
        return order != null ? order.getId() : null;
    }
}
