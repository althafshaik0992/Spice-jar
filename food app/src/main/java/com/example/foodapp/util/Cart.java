package com.example.foodapp.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class Cart {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.08"); // 8%
    private final Map<Long, CartItem> items = new LinkedHashMap<>();

    public List<CartItem> getItems() {
        return new ArrayList<>(items.values());
    }

    public boolean isEmpty() {
        return items == null || items.isEmpty();
    }

    public void add(CartItem item) {
        CartItem existing = items.get(item.getProductId());
        if (existing == null) {
            items.put(item.getProductId(), item);
        } else {
            existing.setQty(existing.getQty() + item.getQty());
        }
    }

    public void update(Long productId, int qty) {
        if (!items.containsKey(productId)) return;
        if (qty <= 0) {
            items.remove(productId);
        } else {
            items.get(productId).setQty(qty);
        }
    }

    public void remove(Long productId) {
        items.remove(productId);
    }

    public void clear() {
        items.clear();
    }

    /** Subtotal (backward compatible with your existing templates) */
    public BigDecimal getTotal() {
        return getSubtotal();
    }

    /** Subtotal of all items */
    public BigDecimal getSubtotal() {
        return items.values().stream()
                .map(CartItem::getSubtotal) // price * qty inside CartItem
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Tax amount = subtotal * 8% */
    public BigDecimal getTax() {
        return getSubtotal()
                .multiply(TAX_RATE)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Grand total = subtotal + tax */
    public BigDecimal getGrandTotal() {
        return getSubtotal()
                .add(getTax())
                .setScale(2, RoundingMode.HALF_UP);
    }
}
