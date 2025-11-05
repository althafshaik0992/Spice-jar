package com.example.foodapp.util;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@Setter
@NoArgsConstructor
public class CartItem implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long productId;
    private String name;            // we keep your "Product — 200 g" display name here
    private int qty;
    private BigDecimal price;       // unit price
    private String imageUrl;

    // ✅ New fields so Thymeleaf can render them directly
    private String description;     // short description to show under name
    private Integer weightGrams;    // e.g., 200 (for “200 g”)

    public CartItem(Long productId, String name, int qty, BigDecimal price, String imageUrl) {
        this.productId = productId;
        this.name = name;
        this.qty = qty;
        this.price = price;
        this.imageUrl = imageUrl;
    }

    // ✅ Convenience ctor that sets description & weight too
    public CartItem(Long productId,
                    String name,
                    int qty,
                    BigDecimal price,
                    String imageUrl,
                    String description,
                    Integer weightGrams) {
        this(productId, name, qty, price, imageUrl);
        this.description = description;
        this.weightGrams = weightGrams;
    }

    public BigDecimal getSubtotal() {
        BigDecimal p = price == null ? BigDecimal.ZERO : price;
        return p.multiply(BigDecimal.valueOf(Math.max(qty, 0)))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
