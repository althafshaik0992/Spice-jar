package com.example.foodapp.service;

import com.example.foodapp.model.Order;
import com.example.foodapp.model.OrderItem;
import com.example.foodapp.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

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

    public Order findById(Long id) {
        return repo.findById(id).orElse(null);
    }

    /**
     * Returns the newest order placed by the given user, or null if none.
     */
    public Order findLatestForUser(Long userId) {
        return repo.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);
    }

    /**
     * Filters and sorts orders for the admin/orders page.
     * All parameters are optional:
     *  - q: matches customerName, address, username, or any item.productName (best-effort)
     *  - from/to: createdAt (inclusive from, inclusive to by day)
     *  - sort: "dateAsc" | "dateDesc" (default newest first)
     */
    public List<Order> findOrders(String q, LocalDate from, LocalDate to, String sort) {
        // load all first (simple + reliable); if you prefer DB-side filtering, we can convert to Specifications later
        List<Order> list = repo.findAll();

        return list.stream()
                .filter(o -> {
                    if (from != null && (o.getCreatedAt() == null || o.getCreatedAt().toLocalDate().isBefore(from))) {
                        return false;
                    }
                    if (to != null && (o.getCreatedAt() == null || o.getCreatedAt().toLocalDate().isAfter(to))) {
                        return false;
                    }
                    if (q != null && !q.isBlank()) {
                        String needle = q.toLowerCase().trim();
                        boolean hit =
                                (o.getCustomerName() != null && o.getCustomerName().toLowerCase().contains(needle)) ||
                                        (o.getAddress() != null && o.getAddress().toLowerCase().contains(needle)) ||
                                        (o.getUsername() != null && o.getUsername().toLowerCase().contains(needle));
                        // item names (guard against nulls)
                        if (!hit && o.getItems() != null) {
                            hit = o.getItems().stream().anyMatch(it ->
                                    it != null &&
                                            it.getProductName() != null &&
                                            it.getProductName().toLowerCase().contains(needle)
                            );
                        }
                        return hit;
                    }
                    return true;
                })
                .sorted(getComparator(sort))
                .collect(Collectors.toList());
    }

    private Comparator<Order> getComparator(String sort) {
        Comparator<Order> byDateAsc = Comparator.comparing(
                Order::getCreatedAt,
                // nulls last so null createdAt doesn’t crash
                (a, b) -> {
                    if (Objects.equals(a, b)) return 0;
                    if (a == null) return 1;
                    if (b == null) return -1;
                    return a.compareTo(b);
                }
        );
        if ("dateAsc".equalsIgnoreCase(sort)) {
            return byDateAsc;
        }
        // default newest first
        return byDateAsc.reversed();
    }

    public Order markPaid(Long orderId) {
        Order o = findById(orderId);
        if (o == null) return null;
        o.setStatus("PAID");
        return repo.save(o);
    }

    public void markPendingCod(Long orderId) {
        Order o = findById(orderId);
        if (o == null) return;
        o.setStatus("PENDING_COD");
        repo.save(o);
    }






}
