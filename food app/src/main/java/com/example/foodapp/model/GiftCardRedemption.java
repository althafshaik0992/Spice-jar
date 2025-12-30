// src/main/java/com/example/foodapp/model/GiftCardRedemption.java
package com.example.foodapp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Getter
@Setter
public class GiftCardRedemption {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private GiftCard giftCard;

    private Long orderId; // nullable until order is created

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;


    /** NEW FIELD — required so setReason() works */
    @Column(length = 255)
    private String reason;

    private OffsetDateTime createdAt = OffsetDateTime.now();

    // getters & setters
    public Long getId() { return id; }
    public GiftCard getGiftCard() { return giftCard; }
    public void setGiftCard(GiftCard giftCard) { this.giftCard = giftCard; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
