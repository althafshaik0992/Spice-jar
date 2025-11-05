package com.example.foodapp.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "coupons")
public class Coupon {

    public enum Type { PERCENT, FLAT,AMOUNT } // or PERCENT, AMOUNT


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
    private LocalDate startsOn;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(nullable = false)
    private Boolean active = true;



    // ✅ Add BOTH getters for compatibility

    public Boolean isActive() { return active; } // ← this makes Thymeleaf happy

    // ✅ boolean + getter "getActive()" will now exist

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

    public Boolean getActive() { return active; }        // ✅ THIS fixes your error
    public void setActive(Boolean active) { this.active = active; }

    // Convenience helpers
    @Override
    public String toString() {
        return "Coupon{" +
                "code='" + code + '\'' +
                ", type=" + type +
                ", value=" + value +
                ", active=" + active +
                '}';
    }
}
