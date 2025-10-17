// src/main/java/com/example/foodapp/admin/dto/RecentOrderDTO.java
package com.example.foodapp.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RecentOrderDTO(
        Long id,
        String customerName,
        BigDecimal grandTotal,
        String status,
        LocalDateTime createdAt
) {}
