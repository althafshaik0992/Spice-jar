// src/main/java/com/example/foodapp/repository/GiftCardRedemptionRepository.java
package com.example.foodapp.repository;

import com.example.foodapp.model.GiftCardRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GiftCardRedemptionRepository extends JpaRepository<GiftCardRedemption, Long> {
    List<GiftCardRedemption> findByGiftCardIdOrderByCreatedAtDesc(Long giftCardId);
}
