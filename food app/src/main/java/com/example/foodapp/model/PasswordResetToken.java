// src/main/java/com/example/foodapp/model/PasswordResetToken.java
package com.example.foodapp.model;

import jakarta.persistence.*;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
public class PasswordResetToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable=false, unique=true, length=120)
    private String token;

    @Setter
    @ManyToOne(optional=false, fetch=FetchType.LAZY)
    private User user;

    @Column(nullable=false)
    private LocalDateTime expiresAt;

    @Column(nullable=false)
    private boolean used = false;

    public static PasswordResetToken create(User user, long minutesValid) {
        PasswordResetToken t = new PasswordResetToken();
        t.user = user;
        t.token = UUID.randomUUID().toString();
        t.expiresAt = LocalDateTime.now().plusMinutes(minutesValid);
        return t;
    }

    // getters/setters …
    public Long getId(){return id;}
    public String getToken(){return token;}

    public User getUser(){return user;}

    public LocalDateTime getExpiresAt(){return expiresAt;}
    public void setExpiresAt(LocalDateTime expiresAt){this.expiresAt=expiresAt;}
    public boolean isUsed(){return used;}
    public void setUsed(boolean used){this.used=used;}
}
