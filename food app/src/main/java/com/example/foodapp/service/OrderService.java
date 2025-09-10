// src/main/java/com/example/foodapp/service/OrderService.java
package com.example.foodapp.service;

import com.example.foodapp.model.Order;
import com.example.foodapp.repository.OrderRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.08");

    private final OrderRepository repo;

    public OrderService(OrderRepository repo) {
        this.repo = repo;
    }

    /* ---------------- Basic CRUD ---------------- */

    public Order save(Order o) {
        return repo.save(o);
    }

    public Order findById(Long id) {
        return repo.findById(id).orElse(null);
    }

    public List<Order> findAll() {
        return repo.findAll();
    }

    /* ---------------- Derived totals (when not persisted) ---------------- */

    /** Returns subtotal (we store it in Order.total in your schema). */
    public BigDecimal getSubtotal(Order o) {
        return nz(o.getTotal());
    }

    /** Returns tax = 8% of subtotal, rounded to 2dp. */
    public BigDecimal getTax(Order o) {
        return getSubtotal(o)
                .multiply(TAX_RATE)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Returns grandTotal = subtotal + tax, rounded to 2dp. */
    public BigDecimal getGrandTotal(Order o) {
        return getSubtotal(o)
                .add(getTax(o))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** If you ever want to materialize totals into transient fields on the entity before rendering. */
    public void applyTaxAndGrandTotal(Order o) {
        // If you later add fields like o.setTax(), o.setGrandTotal(), set them here.
        // Currently your templates often call order.subtotal/tax/grandTotal getters on the entity.
        // If those getters don't exist on Order, compute in controller and put into the model.
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /* ---------------- Queries / filters ---------------- */

    /**
     * Filter by free text (customerName/address/username/item names) and date range,
     * then sort in-memory by createdAt.
     *
     * REQUIREMENT: OrderRepository must extend JpaSpecificationExecutor<Order>.
     */
    public List<Order> findOrders(String q, LocalDate from, LocalDate to, String sort) {
        Specification<Order> spec = (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();

            // Date range on createdAt
            if (from != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            }
            if (to != null) {
                preds.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
            }

            // Free text across several fields + item productName
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";

                Join<Object, Object> items = root.join("items", JoinType.LEFT);

                Predicate pCustomer = cb.like(cb.lower(root.get("customerName")), like);
                Predicate pAddress  = cb.like(cb.lower(root.get("address")), like);
                Predicate pUser     = cb.like(cb.lower(root.get("username")), like);
                Predicate pItem     = cb.like(cb.lower(items.get("productName")), like);

                preds.add(cb.or(pCustomer, pAddress, pUser, pItem));

                // Avoid duplicates when joining items
                query.distinct(true);
            }

            return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(new Predicate[0]));
        };

        List<Order> result = repo.findAll(spec);

        // In-memory sort options (you can expand this as needed)
        if (sort != null) {
            switch (sort) {
                case "dateAsc"  -> result.sort(Comparator.comparing(Order::getCreatedAt));
                case "dateDesc" -> result.sort(Comparator.comparing(Order::getCreatedAt).reversed());
                default -> { /* no-op */ }
            }
        }

        return result;
    }

    /** Latest order for a user (used when orderId is not passed to payment page). */
    public Order findLatestForUser(Long userId) {
        Optional<Order> latest = repo.findTopByUserIdOrderByCreatedAtDesc(userId);
        return latest.orElse(null);
    }

    /* ---------------- Status updates (safe) ---------------- */

    public Order markPaid(Long orderId) {
        Order o = findById(orderId);
        if (o == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }
        o.setStatus("PAID");
        return save(o);
    }

    public Order markPendingCod(Long orderId) {
        Order o = findById(orderId);
        if (o == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }
        o.setStatus("PENDING_COD");
        return save(o);
    }
}
