package com.example.foodapp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Setter
@Getter
@Entity
@Table(name = "orders") // keep table name "orders"
public class Order {

    // --- getters/setters ---
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username")
    private String username;

    private String customerName;
    private String address;

    private LocalDateTime createdAt;

    // store subtotal
    private BigDecimal total;

    private String status;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id")
    private List<OrderItem> items;

    public Order() {}

    // --- Derived calculations ---
    public BigDecimal getSubtotal() {
        return total == null ? BigDecimal.ZERO : total;
    }

    public BigDecimal getTax() {
        return getSubtotal().multiply(new BigDecimal("0.08"))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public BigDecimal getGrandTotal() {
        return getSubtotal().add(getTax())
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
