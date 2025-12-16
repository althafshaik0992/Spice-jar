// src/main/java/com/spicejar/account/PaymentMethodRepository.java
package com.example.foodapp.repository;

import com.example.foodapp.model.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    List<PaymentMethod> findByUserIdOrderByDefaultMethodDescCreatedAtDesc(Long userId);


    PaymentMethod findByCodeIgnoreCase(String code);

    Optional<PaymentMethod> findByCode(String code);

    Optional<PaymentMethod> findFirstByCodeIgnoreCase(String code);
}
