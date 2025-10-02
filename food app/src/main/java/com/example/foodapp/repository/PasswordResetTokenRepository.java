// src/main/java/com/example/foodapp/repository/PasswordResetTokenRepository.java
package com.example.foodapp.repository;

import com.example.foodapp.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
}
