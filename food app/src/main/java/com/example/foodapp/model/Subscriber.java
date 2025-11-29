// src/main/java/com/example/foodapp/model/Subscriber.java
package com.example.foodapp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscribers", indexes = {
        @Index(name="ix_subscribers_email", columnList = "email", unique = true),
        @Index(name="ix_subscribers_status", columnList = "status")
})
@Getter
@Setter
public class Subscriber {

    public enum Status { PENDING, ACTIVE, UNSUBSCRIBED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(nullable = false, length = 64)
    private String confirmToken;

    @Column(nullable = false, length = 64)
    private String unsubToken;

    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime lastSentAt;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }
    // getters/setters ...
}
