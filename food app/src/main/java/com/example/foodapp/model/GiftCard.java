// src/main/java/com/example/foodapp/model/GiftCard.java
package com.example.foodapp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "gift_card")
@Getter
@Setter
public class GiftCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal originalAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    /** NEW: map currency column (DB already has it) */
    @Column(nullable = false, length = 3)
    private String currency = "USD";   // or "INR", whatever you use

    /** Which user owns this card (null = not redeemed yet) */
    @Column(name = "assigned_user_id")
    private Long assignedUserId;

    private boolean active = true;

    @Column(name = "expires_at")
    private LocalDate expiresOn;

    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "purchased_by_user_id")
    private Long purchasedByUserId;

    /** All redemptions (activity) for this gift card */
    @OneToMany(mappedBy = "giftCard", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GiftCardRedemption> redemptions = new ArrayList<>();

    // --- helpers ---

    public boolean isExpired() {
        // no expiry → NEVER expired
        if (expiresOn == null) return false;

        // if a date exists, check if it's in the past
        return expiresOn.isBefore(LocalDate.now());
    }


    // alias used by Thymeleaf: gc.expiresAt
    public LocalDate getExpiresAt() {
        return expiresOn;
    }

    public void setExpiresAt(LocalDate expiresAt) {
        this.expiresOn = expiresAt;
    }
}
