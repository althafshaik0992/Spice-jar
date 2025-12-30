package com.example.foodapp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "product_variants")
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // weight in grams (e.g., 50, 100, 200)
    @Setter
    @Column(nullable = false)
    private Integer weight;

    @Setter
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Setter
    @Column(nullable = false)
    private Integer stock;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    public ProductVariant() {}

    public ProductVariant(Integer weight, BigDecimal price, Product product) {
        this.weight = weight;
        this.price = price;
        this.product = product;
    }

    // ✅ actually set all fields
    public ProductVariant(Integer weight, BigDecimal price, int stock) {
        this.weight = weight;
        this.price = price;
        this.stock  = stock;
    }


    @Transient
    public String getLabel() {
        // If you have a "weight" field in grams:
        if (weight != null) {
            return weight + " g";
        }
        // Or if you have a "name" or "size" field:
        // if (name != null && !name.isBlank()) return name;

        return id != null ? "Option #" + id : "Option";
    }

}
