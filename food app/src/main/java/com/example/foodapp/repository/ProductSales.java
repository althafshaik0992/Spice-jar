package com.example.foodapp.repository;

public class ProductSales {
    private final String productName;
    private final Long totalQuantity;

    public ProductSales(String productName, Long totalQuantity) {
        this.productName = productName;
        this.totalQuantity = totalQuantity;
    }

    public String getProductName() { return productName; }
    public Long getTotalQuantity() { return totalQuantity; }
}
