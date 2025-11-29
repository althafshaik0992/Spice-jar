// src/main/java/com/example/foodapp/web/NewsletterController.java
package com.example.foodapp.controller;

import com.example.foodapp.service.NewsletterService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;

@Controller
public class NewsletterController {
    private final NewsletterService service;

    public NewsletterController(NewsletterService service) { this.service = service; }

    @PostMapping("/subscribe")
    @ResponseBody
    public ResponseEntity<?> subscribe(@RequestParam String email, HttpServletRequest req) {
        String base = baseUrl(req);
        service.requestSubscribe(email, base);
        return ResponseEntity.ok(Map.of("status","ok","message","Check your email to confirm."));
    }

    @GetMapping("/newsletter/confirm")
    public ResponseEntity<?> confirm(@RequestParam String token, HttpServletRequest req) {
        boolean ok = service.confirm(token, baseUrl(req));
        String to = ok ? "/?sub=confirmed" : "/?sub=invalid";
        return ResponseEntity.status(302).location(URI.create(to)).build();
    }

    @GetMapping("/newsletter/unsubscribe")
    public ResponseEntity<?> unsubscribe(@RequestParam String token) {
        boolean ok = service.unsubscribe(token);
        String to = ok ? "/?sub=unsubscribed" : "/?sub=invalid";
        return ResponseEntity.status(302).location(URI.create(to)).build();
    }

    private String baseUrl(HttpServletRequest r) {
        String scheme = r.getHeader("X-Forwarded-Proto") != null ? r.getHeader("X-Forwarded-Proto") : r.getScheme();
        String host   = r.getHeader("X-Forwarded-Host")  != null ? r.getHeader("X-Forwarded-Host")  : r.getServerName();
        int port      = r.getHeader("X-Forwarded-Port")  != null ? Integer.parseInt(r.getHeader("X-Forwarded-Port")) : r.getServerPort();
        boolean std   = (scheme.equals("http") && port==80) || (scheme.equals("https") && port==443);
        return scheme + "://" + host + (std ? "" : ":" + port);
    }
}
