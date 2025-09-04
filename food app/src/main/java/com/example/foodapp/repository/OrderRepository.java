package com.example.foodapp.repository;

import com.example.foodapp.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    List<Order> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);


    Optional<Order> findByIdAndUserId(Long id, Long userId);
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
    // src/main/java/com/example/foodapp/repository/OrderRepository.java

}