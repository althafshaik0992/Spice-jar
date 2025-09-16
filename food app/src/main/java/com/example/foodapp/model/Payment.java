// src/main/java/com/example/foodapp/model/Payment.java
package com.example.foodapp.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 8)
    private String currency; // e.g. "USD"

    @Column(nullable = false, length = 20)
    private String status;   // INITIATED, SUCCEEDED, FAILED, PENDING, CANCELED

    @Column(nullable = false, length = 20)
    private String provider; // STRIPE, PAYPAL, COD

    /** Provider “order” id (Stripe PaymentIntent id, PayPal Order id, etc) */
    @Column(name = "provider_payment_id", length = 80, unique = true)
    private String providerPaymentId;

    /** Final transaction / capture id (Stripe charge/capture id, PayPal capture id) */
    @Column(name = "txn_id", length = 80)
    private String txnId;

    private LocalDateTime createdAt = LocalDateTime.now();

    // --- getters/setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getProviderPaymentId() { return providerPaymentId; }
    public void setProviderPaymentId(String providerPaymentId) { this.providerPaymentId = providerPaymentId; }

    public String getTxnId() { return txnId; }
    public void setTxnId(String txnId) { this.txnId = txnId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
