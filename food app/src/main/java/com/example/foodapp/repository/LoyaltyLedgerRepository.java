package com.example.foodapp.repository;

import com.example.foodapp.model.LoyaltyLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;



public interface LoyaltyLedgerRepository
        extends JpaRepository<LoyaltyLedger, Long> {

    List<LoyaltyLedger> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);
}