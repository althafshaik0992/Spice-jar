package com.example.foodapp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

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
    private int quantity;
    @Getter
    @Setter
    private BigDecimal price;

    @Getter
    @Setter
    private String imageUrl;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;



    public OrderItem() {}

    /** Line total = price * quantity */
    public BigDecimal getLineTotal() {
        BigDecimal p = (price == null ? BigDecimal.ZERO : price);
        int q = quantity;
        return p.multiply(BigDecimal.valueOf(q));
    }


}
