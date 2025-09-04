package com.example.foodapp.service;

import com.example.foodapp.model.Order;

import java.time.LocalDate;
import java.util.List;

// UserOrderService.java (interface)
public interface UserOrderService {
    List<Order> findByUser(Long userId);
    List<Order> filterUserOrders(List<Order> orders, String q, LocalDate from, LocalDate to);
    Order findById(Long id);

}

