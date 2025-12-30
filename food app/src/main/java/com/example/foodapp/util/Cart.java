package com.example.foodapp.util;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;



@Getter
@Setter
@Entity
@Table(name = "cart")
public class Cart {


    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public enum Status { OPEN, CHECKED_OUT, ABANDONED }

    // === getters ===
    @Getter
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @Where(clause = "saved_for_later = false")   // Hibernate: auto-filter active items
    private List<CartItem> items = new ArrayList<>();

    // NEW: Saved-for-later items
    @Getter
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @Where(clause = "saved_for_later = true")    // Hibernate: auto-filter saved items
    private List<CartItem> savedForLater = new ArrayList<>();

    @Getter
    @Setter
    private BigDecimal discount = BigDecimal.ZERO;


    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;




    @Getter
    @Setter
    private String appliedCouponCode;

    public void recompute(BigDecimal taxRate){
        BigDecimal subtotal = items.stream()
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (discount.compareTo(subtotal) > 0) discount = subtotal;
        BigDecimal base = subtotal.subtract(discount);
        BigDecimal tax = base.multiply(taxRate).setScale(2, BigDecimal.ROUND_HALF_UP);
        BigDecimal grandTotal = base.add(tax).setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    public int countItems(){ return items.stream().mapToInt(CartItem::getQty).sum(); }


    // === cart operations ===
    public void addItem(CartItem item) {
        // merge quantity if product already in cart
        var existing = items.stream()
                .filter(i -> i.getProductId().equals(item.getProductId()))
                .findFirst();
        if (existing.isPresent()) {
            existing.get().setQty(existing.get().getQty() + item.getQty());
        } else {
            items.add(item);
        }
    }

    public void addItem(Long productId, String name, int qty, BigDecimal price, String imageUrl) {
        addItem(new CartItem(productId, name, qty, price, imageUrl));
    }

    public void removeItem(Long productId) {
        items.removeIf(i -> i.getProductId().equals(productId));
    }

    public void addToSavedForLater(CartItem item) {
        savedForLater.add(item);
    }

    public void clear() {
        items.clear();
        savedForLater.clear();
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    // === totals ===
    public BigDecimal getSubtotal() {
        return items.stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getTax() {
        return getSubtotal()
                .multiply(new BigDecimal("0.08"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getGrandTotal() {
        return getSubtotal()
                .add(getTax())
                .setScale(2, RoundingMode.HALF_UP);
    }


    public int getTotalQuantity() {
        return items.stream()
                .mapToInt(CartItem::getQty)
                .sum();
    }

    // --- helpers ---

    public void removeItem(CartItem item) {
        this.items.remove(item);
        item.setCart(null);
    }


}
