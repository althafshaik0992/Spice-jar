package com.example.foodapp.repository;

import com.example.foodapp.util.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> { }