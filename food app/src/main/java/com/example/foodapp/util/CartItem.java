package com.example.foodapp.util;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@Setter
public class CartItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long productId;
    private String name;
    private int qty;
    private BigDecimal price;
    private String imageUrl;

    public CartItem(Long productId, String name, int qty, BigDecimal price, String imageUrl) {
        this.productId = productId;
        this.name = name;
        this.qty = qty;
        this.price = price == null ? BigDecimal.ZERO : price;
        this.imageUrl = imageUrl;
    }

    public BigDecimal getSubtotal() {
        return price
                .multiply(BigDecimal.valueOf(qty))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
