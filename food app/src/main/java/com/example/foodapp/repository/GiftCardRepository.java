// src/main/java/com/example/foodapp/repository/GiftCardRepository.java
package com.example.foodapp.repository;

import com.example.foodapp.model.GiftCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GiftCardRepository extends JpaRepository<GiftCard, Long> {

    Optional<GiftCard> findByCodeIgnoreCase(String code);

    List<GiftCard> findByAssignedUserId(Long userId);

    List<GiftCard> findByAssignedUserIdOrderByCreatedAtDesc(Long userId);

    // 🔹 NEW: show cards either assigned to the user OR bought by the user
    List<GiftCard> findByAssignedUserIdOrPurchasedByUserIdOrderByCreatedAtDesc(
            Long assignedUserId,
            Long purchasedByUserId
    );

    Optional<GiftCard> findByIdAndAssignedUserId(Long id, Long userId);
}
