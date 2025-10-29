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
@Table(name = "orders") // table name "orders"
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

    // The new confirmation number field
    private String confirmationNumber;

    // we’ll treat this as "snapshot subtotal" (persisted at save time)
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

    public Order() {}

    // --- Derived calculations ---
    /** Subtotal = sum of line items (or fallback to total if no items) */
    public BigDecimal getSubtotal() {
        if (items != null && !items.isEmpty()) {
            return items.stream()
                    .map(OrderItem::getLineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return total == null ? BigDecimal.ZERO : total.setScale(2, RoundingMode.HALF_UP);
    }

    /** Tax = subtotal * 8% */
    public BigDecimal getTax() {
        return getSubtotal()
                .multiply(new BigDecimal("0.08"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Grand total = subtotal + tax */
    public BigDecimal getGrandTotal() {
        return getSubtotal()
                .add(getTax())
                .setScale(2, RoundingMode.HALF_UP);
    }


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
