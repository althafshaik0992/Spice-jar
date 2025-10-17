// src/main/java/com/example/foodapp/admin/dto/DashboardMetrics.java
package com.example.foodapp.model;

import java.math.BigDecimal;

public record DashboardMetrics(
        long totalOrders,
        BigDecimal totalRevenue,
        long totalMenu,
        long totalCategories
) {}
