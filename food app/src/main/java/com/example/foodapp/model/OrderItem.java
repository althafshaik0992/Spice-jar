package com.example.foodapp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(schema = "order_items")
public class OrderItem {

    // --- getters/setters ---
    @Setter
    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Getter
    private Long productId;
    @Setter
    @Getter
    private String productName;
    @Getter
    @Setter
    private Integer quantity;


    @Getter
    @Setter
    private BigDecimal price;

    @Getter
    @Setter
    private String imageUrl;


    @Getter
    @Setter
    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    // IMPORTANT: map to unit_price
    @Getter
    @Setter
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;


    @Getter
    @Setter
    private Long variantId;

    // --- NEW FIELDS FOR RETURNS ---
    @Setter
    @Getter
    private Boolean returned = false;

    @Setter
    @Getter
    // item was successfully returned
    private Boolean returnRequested = false;   // user requested return

    // Optional: status string
    @Getter
    @Setter
    private String returnStatus; // REQUESTED, APPROVED, REJECTED, REFUNDED
    public Boolean isReturned() {
        return returned != null && returned;
    }

    public Boolean isReturnRequested() {
        return returnRequested != null && returnRequested;
    }


    public OrderItem() {}

    /** Line total = price * quantity */
    @PrePersist @PreUpdate
    private void syncAndCompute() {
        // if someone sets price by mistake, mirror it
        if (unitPrice == null && price != null) unitPrice = price;
        if (price == null && unitPrice != null) price = unitPrice; // if you still read price anywhere

        BigDecimal p = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        int q = quantity != null ? quantity : 0;
        lineTotal = p.multiply(BigDecimal.valueOf(q)).setScale(2, RoundingMode.HALF_UP);
    }




}
