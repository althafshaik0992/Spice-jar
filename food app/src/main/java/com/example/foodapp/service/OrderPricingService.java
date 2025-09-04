//// src/main/java/com/example/foodapp/service/OrderPricingService.java
//package com.example.foodapp.service;
//
//import com.example.foodapp.model.Order;
//import com.example.foodapp.model.OrderItem;
//
//import java.math.BigDecimal;
//import java.math.RoundingMode;
//import java.util.List;
//
//public class OrderPricingService {
//
//    private static final BigDecimal TAX_RATE = new BigDecimal("0.08"); // 8%
//
//    /** Recalculate subtotal, tax, grandTotal from items. */
//    public static void price(Order order) {
//        BigDecimal subtotal = sum(order.getItems());
//        BigDecimal tax = subtotal.multiply(TAX_RATE);
//        BigDecimal grand = subtotal.add(tax);
//
//        // normalize money (2 decimals)
//        subtotal = money(subtotal);
//        tax = money(tax);
//        grand = money(grand);
//
//        order.setTotal(subtotal);
//        order.setTax(tax);
//        order.setGrandTotal(grand);
//    }
//
//    private static BigDecimal sum(List<OrderItem> items) {
//        if (items == null || items.isEmpty()) return BigDecimal.ZERO;
//        BigDecimal total = BigDecimal.ZERO;
//        for (OrderItem it : items) {
//            if (it.getPrice() == null || it.getQuantity() == null) continue;
//            total = total.add(it.getPrice().multiply(BigDecimal.valueOf(it.getQuantity())));
//        }
//        return total;
//    }
//
//    public static BigDecimal money(BigDecimal x) {
//        if (x == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
//        return x.setScale(2, RoundingMode.HALF_UP);
//    }
//}
