package com.example.foodapp.Ai;

public interface CartService {
    record CartLine(Long productId, String productName, int qty, java.math.BigDecimal lineTotal){}
    void add(String sessionId, Long productId, int qty);
    java.util.List<CartLine> items(String sessionId);
    java.math.BigDecimal total(String sessionId);
    Long freezeToOrder(String sessionId); // creates Order, empties cart
}
