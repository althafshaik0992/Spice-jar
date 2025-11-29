// src/main/java/com/example/foodapp/service/NewsletterService.java
package com.example.foodapp.service;

import com.example.foodapp.model.Subscriber;
import com.example.foodapp.model.Subscriber.Status;
import com.example.foodapp.repository.SubscriberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;

@Service
public class NewsletterService {
    private final SubscriberRepository repo;
    private final EmailService mail;
    private final SecureRandom rng = new SecureRandom();

    public NewsletterService(SubscriberRepository repo, EmailService mail) {
        this.repo = repo;
        this.mail = mail;
    }


    private String token() {
        byte[] b = new byte[24]; rng.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    @Transactional
    public void requestSubscribe(String email, String siteBaseUrl) {
        email = email.trim().toLowerCase();

        var existing = repo.findByEmailIgnoreCase(email).orElse(null);
        if (existing != null && existing.getStatus() == Status.ACTIVE) {
            // already active: silently succeed
            return;
        }

        Subscriber s = existing != null ? existing : new Subscriber();
        s.setEmail(email);
        s.setStatus(Status.PENDING);
        s.setConfirmToken(token());
        if (s.getUnsubToken() == null) s.setUnsubToken(token());
        repo.save(s);

        String confirmLink = siteBaseUrl + "/newsletter/confirm?token=" + s.getConfirmToken();
        String unsubLink   = siteBaseUrl + "/newsletter/unsubscribe?token=" + s.getUnsubToken();

        mail.sendTemplate(
                email,
                "Confirm your SpiceJar subscription",
                "email/confirm",
                Map.of("confirmLink", confirmLink, "unsubLink", unsubLink)
        );
    }

    @Transactional
    public boolean confirm(String token, String siteBaseUrl) {
        var s = repo.findByConfirmToken(token).orElse(null);
        if (s == null) return false;

        s.setStatus(Status.ACTIVE);
        s.setConfirmedAt(java.time.LocalDateTime.now());
        repo.save(s);

        String unsubLink = siteBaseUrl + "/newsletter/unsubscribe?token=" + s.getUnsubToken();

        mail.sendTemplate(
                s.getEmail(),
                "Welcome to SpiceJar! 🎉",
                "email/welcome",
                Map.of("unsubLink", unsubLink)
        );
        return true;
    }

    @Transactional
    public boolean unsubscribe(String token) {
        var s = repo.findByUnsubToken(token).orElse(null);
        if (s == null) return false;
        s.setStatus(Status.UNSUBSCRIBED);
        repo.save(s);
        return true;
    }
}
