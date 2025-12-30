package com.example.foodapp.repository;

import com.example.foodapp.model.LoyaltyWallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;



public interface LoyaltyWalletRepository
        extends JpaRepository<LoyaltyWallet, Long> {

    Optional<LoyaltyWallet> findByUserId(Long userId);
}
