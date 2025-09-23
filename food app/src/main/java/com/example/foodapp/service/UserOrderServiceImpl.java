package com.example.foodapp.service;

import com.example.foodapp.model.Order;
import com.example.foodapp.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;


@Service
public class UserOrderServiceImpl implements UserOrderService {
    private final OrderRepository repo;

    public UserOrderServiceImpl(OrderRepository repo) {
        this.repo = repo;
    }


    @Override
    public List<Order> findByUser(Long userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public Order findById(Long id) {
        return repo.findById(id).orElse(null);
    }

    @Override
    public List<Order> filterUserOrders(List<Order> orders, String q, LocalDate from, LocalDate to) {
        return orders.stream().filter(o -> {
            boolean ok = true;
            if (q != null && !q.isBlank()) {
                String needle = q.toLowerCase();
                boolean match =
                        (o.getCustomerName() != null && o.getCustomerName().toLowerCase().contains(needle)) ||
                                (o.getAddress() != null && o.getAddress().toLowerCase().contains(needle)) ||
                                // Filter by confirmation number as well
                                (o.getConfirmationNumber() != null && o.getConfirmationNumber().toLowerCase().contains(needle)) ||
                                (o.getItems() != null && o.getItems().stream().anyMatch(
                                        it -> it.getProductName() != null && it.getProductName().toLowerCase().contains(needle)
                                ));
                ok &= match;
            }
            if (from != null) {
                ok &= (o.getCreatedAt() != null && !o.getCreatedAt().toLocalDate().isBefore(from));
            }
            if (to != null) {
                ok &= (o.getCreatedAt() != null && !o.getCreatedAt().toLocalDate().isAfter(to));
            }
            return ok;
        }).collect(Collectors.toList());
    }
}
