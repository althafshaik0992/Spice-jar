package com.example.foodapp.service;

import com.example.foodapp.model.Order;
import com.example.foodapp.repository.OrderRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import jakarta.persistence.criteria.*;

@Service
public class OrderService {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.08");
    private final OrderRepository repo;

    public OrderService(OrderRepository repo) {
        this.repo = repo;
    }

    public Order save(Order o) {
        return repo.save(o);
    }

    public List<Order> findAll() {
        return repo.findAll();
    }

    public List<Order> findOrders(String q, LocalDate from, LocalDate to, String sort) {
        // Build the JPA Specification from the provided filters
        Specification<Order> spec = (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();

            // Date range on createdAt
            if (from != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            }
            if (to != null) {
                // end of day
                preds.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
            }

            // Free-text 'q' over customerName, address, username, and item productName
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";

                // Left join items for product name search
                Join<Object, Object> items = root.join("items", JoinType.LEFT);

                Predicate pCustomer = cb.like(cb.lower(root.get("customerName")), like);
                Predicate pAddress = cb.like(cb.lower(root.get("address")), like);
                Predicate pUser = cb.like(cb.lower(root.get("username")), like);
                Predicate pItem = cb.like(cb.lower(items.get("productName")), like);

                preds.add(cb.or(pCustomer, pAddress, pUser, pItem));

                // Distinct because of LEFT JOIN duplicates
                query.distinct(true);
            }

            return cb.and(preds.toArray(new Predicate[0]));
        };

        // Execute the query using the specification and repository
        List<Order> result = repo.findAll( spec);

        // Apply sorting to the in-memory list
        if (sort != null) {
            switch (sort) {
                case "dateAsc"  -> result.sort(Comparator.comparing(Order::getCreatedAt));
                case "dateDesc" -> result.sort(Comparator.comparing(Order::getCreatedAt).reversed());
                // Add more sorting cases as needed
            }
        }

        return result;
    }

}