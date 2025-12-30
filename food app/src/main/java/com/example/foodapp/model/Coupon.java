package com.example.foodapp.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "coupons")
public class Coupon {

    public enum Type {
        PERCENT,
        AMOUNT   // behaves like a flat dollar discount
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Type type = Type.PERCENT;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal value = BigDecimal.ZERO;

    @Column(name = "min_subtotal", precision = 10, scale = 2)
    private BigDecimal minSubtotal = BigDecimal.ZERO;

    @Column(name = "starts_on")
    private LocalDate startsOn;        // nullable = “starts immediately”

    @Column(name = "expires_on")
    private LocalDate expiresOn;       // nullable = “no expiry”

    @Column(nullable = false)
    private Boolean active = true;

    // ---------- Convenience helpers ----------

    /** Required for Thymeleaf `${c.active}` etc. */
    public Boolean getActive() {
        return active;
    }
    public Boolean isActive() {
        return active;
    }

    /** True when there is no expiry date set. */
    @Transient
    public boolean hasNoExpiry() {
        return expiresOn == null;
    }

    /** True if the coupon is expired as of the given date. */
    @Transient
    public boolean isExpired(LocalDate today) {
        return expiresOn != null && expiresOn.isBefore(today.plusDays(1));
    }

    /** True if the coupon is currently valid for the given subtotal. */
    @Transient
    public boolean isCurrentlyValid(LocalDate today, BigDecimal subtotal) {
        if (!Boolean.TRUE.equals(active)) return false;
        if (startsOn != null && today.isBefore(startsOn)) return false;
        if (expiresOn != null && today.isAfter(expiresOn)) return false;
        if (minSubtotal != null && subtotal != null &&
                subtotal.compareTo(minSubtotal) < 0) return false;
        return true;
    }

    // ---------- Getters & Setters ----------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }

    public BigDecimal getMinSubtotal() { return minSubtotal; }
    public void setMinSubtotal(BigDecimal minSubtotal) { this.minSubtotal = minSubtotal; }

    public LocalDate getStartsOn() { return startsOn; }
    public void setStartsOn(LocalDate startsOn) { this.startsOn = startsOn; }

    public LocalDate getExpiresOn() { return expiresOn; }
    public void setExpiresOn(LocalDate expiresOn) { this.expiresOn = expiresOn; }

    public void setActive(Boolean active) { this.active = active; }

    @Override
    public String toString() {
        return "Coupon{" +
                "code='" + code + '\'' +
                ", type=" + type +
                ", value=" + value +
                ", minSubtotal=" + minSubtotal +
                ", startsOn=" + startsOn +
                ", expiresOn=" + expiresOn +
                ", active=" + active +
                '}';
    }
}
