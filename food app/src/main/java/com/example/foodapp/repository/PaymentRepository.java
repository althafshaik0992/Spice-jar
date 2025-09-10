// src/main/java/com/example/foodapp/repository/PaymentRepository.java
package com.example.foodapp.repository;

import com.example.foodapp.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByProviderPaymentId(String providerPaymentId);
    Payment findTopByOrderIdOrderByIdDesc(Long orderId);
}
