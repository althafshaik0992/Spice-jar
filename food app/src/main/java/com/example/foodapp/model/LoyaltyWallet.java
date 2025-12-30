package com.example.foodapp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "loyalty_wallet")
@Getter
@Setter
public class LoyaltyWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "points_balance", nullable = false)
    private Integer pointsBalance = 0;

    public LoyaltyWallet() {}

    public LoyaltyWallet(Long userId) {
        this.userId = userId;
        this.pointsBalance = 0;
    }
}
