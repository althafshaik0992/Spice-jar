// com.example.foodapp.util.CartItem
package com.example.foodapp.util;

import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class CartItem {
    private Long productId;
    private String name;
    @Setter
    private int qty;
    private BigDecimal price;
    private String imageUrl; // <-- add this

    public CartItem(Long productId, String name, int qty, BigDecimal price, String imageUrl) {
        this.productId = productId;
        this.name = name;
        this.qty = qty;
        this.price = price == null ? BigDecimal.ZERO : price;
        this.imageUrl = imageUrl;
    }

    // existing ctor if you still use it somewhere
    public CartItem(Long productId, String name, int qty, BigDecimal price) {
        this(productId, name, qty, price, null);
    }

    public BigDecimal getSubtotal() {
        return price
                .multiply(BigDecimal.valueOf(qty))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // getters/setters
    public Long getProductId() { return productId; }
    public String getName() { return name; }
    public int getQty() { return qty; }
    public BigDecimal getPrice() { return price; }
    public String getImageUrl() { return imageUrl; }

    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
