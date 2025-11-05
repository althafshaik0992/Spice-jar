// src/main/java/com/example/foodapp/admin/dto/DayBucket.java
package com.example.foodapp.model;

public record DayBucket(
        String day,   // e.g., "2025-10-01"
        long count
) {}
