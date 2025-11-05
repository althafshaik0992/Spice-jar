// src/main/java/com/example/foodapp/admin/dto/TopProductDTO.java
package com.example.foodapp.model;

public record TopProductDTO(
        Long productId,
        String name,
        long quantity
) {}
