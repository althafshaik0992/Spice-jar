// src/main/java/com/spicejar/account/PaymentMethodRepository.java
package com.example.foodapp.repository;

import com.example.foodapp.model.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    List<PaymentMethod> findByUserIdOrderByDefaultMethodDescCreatedAtDesc(Long userId);
}
