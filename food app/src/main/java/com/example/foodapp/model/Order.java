package com.example.foodapp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Setter
@Getter
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username")
    private String username;

    private String customerName;
    private String address;
    private LocalDateTime createdAt;

    /** Human-readable confirmation number shown to the user. */
    private String confirmationNumber;

    /**
     * Snapshot subtotal saved when the order is created.
     * (Used as a fallback if items are null.)
     */
    private BigDecimal total;

    private String status;

    // Contact + address
    private String email;
    private String phone;
    private String street;
    private String city;
    private String state;
    private String zip;
    private String country;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id")
    private List<OrderItem> items;

    /** How much gift card balance was applied to this order in total. */
    @Column(name = "gift_applied", precision = 10, scale = 2)
    private BigDecimal giftApplied = BigDecimal.ZERO;

    /** Any discount (coupon, promo, etc.) applied to this order (pre-tax). */
    @Column(name = "discount", precision = 10, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    public Order() {
    }

    // ---------- Safe helpers ----------

    public BigDecimal getGiftAppliedSafe() {
        return giftApplied == null
                ? BigDecimal.ZERO
                : giftApplied.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getDiscountSafe() {
        return discount == null
                ? BigDecimal.ZERO
                : discount.setScale(2, RoundingMode.HALF_UP);
    }

    // ---------- Derived calculations ----------

    /**
     * Subtotal = sum of item line totals.

     * If items are not present, fall back to the stored {@code total}.
     */
    public BigDecimal getSubtotal() {
        if (items != null && !items.isEmpty()) {
            return items.stream()
                    .map(OrderItem::getLineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return total == null
                ? BigDecimal.ZERO
                : total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Base used for tax and grand total:
     * (subtotal - discount), never below 0.
     */
    private BigDecimal getTaxBase() {
        BigDecimal base = getSubtotal().subtract(getDiscountSafe());
        if (base.signum() < 0) {
            base = BigDecimal.ZERO;
        }
        return base.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Tax = (subtotal - discount) * 8%.
     */
    public BigDecimal getTax() {
        return getTaxBase()
                .multiply(new BigDecimal("0.08"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Grand total = (subtotal - discount) + tax.
     */
    public BigDecimal getGrandTotal() {
        return getTaxBase()
                .add(getTax())
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * What the customer still needs to pay online
     * after discount + any gift card amount applied.
     */
    @Transient
    public BigDecimal getRemainingToPay() {
        BigDecimal totalToPay = getGrandTotal();        // already after discount
        BigDecimal applied    = getGiftAppliedSafe();   // gift card applied

        BigDecimal remaining = totalToPay.subtract(applied);
        if (remaining.signum() < 0) {
            remaining = BigDecimal.ZERO;
        }
        return remaining.setScale(2, RoundingMode.HALF_UP);
    }

    // ---------- Utility ----------

    @JsonIgnore
    public String getFullAddress() {
        StringBuilder sb = new StringBuilder();
        if (street != null && !street.isBlank()) sb.append(street);
        if (city != null && !city.isBlank()) sb.append(!sb.isEmpty() ? ", " : "").append(city);
        if (state != null && !state.isBlank()) sb.append(!sb.isEmpty() ? ", " : "").append(state);
        if (zip != null && !zip.isBlank()) sb.append(" ").append(zip);
        return sb.toString();
    }
}
