package com.example.foodapp.util;

import jakarta.persistence.*;
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
@Entity
@Table(name = "cart_items")
public class CartItem implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public enum Type { PRODUCT, GIFT_CARD }

    private Long productId;
    private String name;// we keep your "Product — 200 g" display name here

    @Getter
    @Setter
    private int qty;
    private BigDecimal price;       // unit price
    private String imageUrl;

    // inside CartItem class
    @Column(name = "saved_for_later", nullable = false)
    private boolean savedForLater = false;

    // ✅ New fields so Thymeleaf can render them directly
    private String description;     // short description to show under name
    private Integer weightGrams;


    // RELATION BACK TO CART
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id")
    private Cart cart;



    @Column(name = "variant_id")
    private Long variantId;        // nullable for gift cards

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type = Type.PRODUCT;

    // Generic display + pricing (works for both product and gift card)
    @Column(length = 64)
    private String sku;            // e.g., "GC-EMAIL" or "GC-PHYSICAL"



    @Lob
    @Column(columnDefinition = "TEXT")
    private String metaJson;       // personalization JSON for gift cards (to/from/message/etc.)




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
