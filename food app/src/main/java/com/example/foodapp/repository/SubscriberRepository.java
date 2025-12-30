// src/main/java/com/example/foodapp/repository/SubscriberRepository.java
package com.example.foodapp.repository;

import com.example.foodapp.model.Subscriber;
import com.example.foodapp.model.Subscriber.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriberRepository extends JpaRepository<Subscriber, Long> {
    Optional<Subscriber> findByEmailIgnoreCase(String email);
    Optional<Subscriber> findByConfirmToken(String token);
    Optional<Subscriber> findByUnsubToken(String token);
    List<Subscriber> findByStatus(Status status);
}
