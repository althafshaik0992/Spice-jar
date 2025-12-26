package com.example.foodapp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "loyalty_ledger")
@Getter
@Setter
public class LoyaltyLedger {

    public LoyaltyLedger(Long userId, Long orderId, Type type, int i, String redeemedAtCheckout) {
        this.userId = userId;
        this.orderId = orderId;
        this.type = type;
    }

    public enum Type { EARN, REDEEM }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long orderId;

    @Enumerated(EnumType.STRING)
    private Type type;

    private int points;
    private String note;

    private LocalDateTime createdAt = LocalDateTime.now();

    public LoyaltyLedger(Long id, Long userId, Long orderId, Type type, int points, String note, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.orderId = orderId;
        this.type = type;
        this.points = points;
        this.note = note;
        this.createdAt = createdAt;
    }

    public LoyaltyLedger() {
    }
    // constructors + getters
}

