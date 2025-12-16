// src/main/java/com/spicejar/account/PaymentMethod.java
package com.example.foodapp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "payment_methods")
@Getter
@Setter
public class PaymentMethod {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User user;




    // We store only NON-sensitive info (never full PAN or CVC!)
    private String brand;     // e.g. "Visa", "Mastercard"
    private String last4;     // e.g. "4242"
    private Integer expMonth; // 1-12
    private Integer expYear;  // YYYY
    private String nameOnCard;

    // quick billing address (optional)
    private String billingLine1;
    private String billingLine2;
    private String billingCity;
    private String billingState;
    private String billingCountry;
    private String billingZip;



    private String code;       // e.g. "STRIPE", "PAYPAL", "COD"
    private String displayName;

    private boolean defaultMethod;

    private Instant createdAt = Instant.now();




}

