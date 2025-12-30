package com.example.foodapp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    // store /images/xyz.jpg after you save the uploaded file
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private Integer weight;

    @Column(nullable = false)
    private Integer stock;

    // total stock for this product (base or sum of variants).
    // You can still persist this if you want, but the helpers below
    // compute everything on the fly as well.
    @Getter
    @Setter
    private Integer stockQty = 0;

    @Getter
    @Setter
    // optional: custom low-stock threshold per product
    private Integer lowStockThreshold;

    @Getter
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    private List<Review> reviews = new ArrayList<>();

    public Product() {}

    public Product(Long id, String name, String description, BigDecimal price,
                   String imageUrl, Category category, Integer weight, Integer stock) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.imageUrl = imageUrl;
        this.category = category;
        this.weight = weight;
        this.stock = stock;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public Integer getWeight() { return weight; }
    public void setWeight(Integer weight) { this.weight = weight; }

    public void setVariants(List<ProductVariant> variants) {
        this.variants = variants;
    }

    // --- NEW HELPERS ---

    /**
     * Total stock for this product:
     *  - if variants exist, sum variant.stock
     *  - otherwise use base stock
     */
    public int getTotalStock() {
        if (variants != null && !variants.isEmpty()) {
            return variants.stream()
                    .map(ProductVariant::getStock)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();
        }
        return stock != null ? stock : 0;
    }

    /**
     * Minimum variant stock (ignoring nulls).
     * Returns null if there are no variants or all stocks null.
     */
    public Integer getMinVariantStock() {
        if (variants == null || variants.isEmpty()) {
            return null;
        }

        return variants.stream()
                .map(ProductVariant::getStock)
                .filter(s -> s != null && s > 0)  // ✅ ignore null and 0
                .min(Integer::compareTo)
                .orElse(null);                    // all null/0 → return null
    }


    /**
     * Recompute and cache total stock into stockQty (optional).
     * Call this from service if you want a persisted snapshot.
     */
    public void recomputeStockQtyFromVariants() {
        this.stockQty = getTotalStock();
    }

    public boolean isOutOfStock() {
        return getTotalStock() <= 0;
    }

    public boolean isLowStock(int defaultThreshold) {
        int total = getTotalStock();
        if (total <= 0) return false; // already out of stock
        int th = (lowStockThreshold == null ? defaultThreshold : lowStockThreshold);
        return total <= th;
    }

    // convenience for full replace (used on save/update)
    public void replaceVariants(List<ProductVariant> newOnes) {
        this.variants.clear();
        if (newOnes != null) this.variants.addAll(newOnes);
        for (ProductVariant v : this.variants) {
            v.setProduct(this);
        }
        // keep cached field in sync
        recomputeStockQtyFromVariants();
    }

    public void addVariant(ProductVariant v) {
        if (v == null) return;
        v.setProduct(this);
        this.variants.add(v);
        // keep cached field in sync
        recomputeStockQtyFromVariants();
    }
}
