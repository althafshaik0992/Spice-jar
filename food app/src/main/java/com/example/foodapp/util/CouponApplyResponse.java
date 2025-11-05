package com.example.foodapp.util;

import java.math.BigDecimal;

public record CouponApplyResponse(
        String status,
        String message,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal tax,
        BigDecimal grandTotal,
        Integer cartCount
) {}
