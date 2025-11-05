// src/main/java/com/example/foodapp/security/OAuth2LoginSuccessHandler.java
package com.example.foodapp.service;

import com.example.foodapp.model.User;
import com.example.foodapp.web.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final EmailServiceWelcome emailService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public OAuth2LoginSuccessHandler(UserService userService, EmailServiceWelcome emailService) {
        this.userService = userService;
        this.emailService = emailService;
    }

//    @Override
//    public void onAuthenticationSuccess(
//            HttpServletRequest request,
//            HttpServletResponse response,
//            Authentication authentication) throws IOException {
//
//        OAuth2User oauth = (OAuth2User) authentication.getPrincipal();
//        Map<String, Object> a = oauth.getAttributes();
//
//        // Google/OpenID standard claims
//        String email  = (String) a.getOrDefault("email", "");
//        String given  = (String) a.getOrDefault("given_name", null);
//        String family = (String) a.getOrDefault("family_name", null);
//        String name   = (String) a.getOrDefault("name", null);
//
//        // Create or fetch user
//        User user = userService.createOAuthUser(email, given, family, name);
//
//        // Welcome email only on first sign-in
//        if (user.getLastLoginAt() == null) {
//            try { emailService.sendWelcomeEmail(user, baseUrl); } catch (Exception ignored) {}
//        }
//
//        // Put lean session object for controllers/Thymeleaf
//        request.getSession(true).setAttribute("USER", user);
//
//        // Touch last login
//        userService.touchLastLogin(user.getId());
//
//        // Go home (or replace with SavedRequest redirect if you prefer)
//        response.sendRedirect("/");
//    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String provider = token.getAuthorizedClientRegistrationId(); // "google" | "facebook"

        OAuth2User oauth = (OAuth2User) authentication.getPrincipal();
        Map<String, Object> a = oauth.getAttributes();

        // --- Normalize profile fields for Google & Facebook ---
        String email = null;
        String first = null;
        String last = null;
        String display = null;
        String username = null;

        if ("google".equalsIgnoreCase(provider)) {
            email   = (String) a.get("email");
            first   = (String) a.getOrDefault("given_name", null);
            last    = (String) a.getOrDefault("family_name", null);
            display = (String) a.getOrDefault("name", null);
            // username: prefer email local-part if present, else random fallback
            username = (email != null && email.contains("@"))
                    ? email.substring(0, email.indexOf('@'))
                    : "gg_" + a.getOrDefault("sub", UUID.randomUUID().toString()).toString().substring(0, 12);

        } else if ("facebook".equalsIgnoreCase(provider)) {
            // For FB you must request scopes: email, public_profile
            // and configure the userInfoUri with fields=id,email,first_name,last_name,name,picture{url} in application.properties
            email   = (String) a.get("email");         // may be null if user hid email
            first   = (String) a.get("first_name");    // may be null if not requested
            last    = (String) a.get("last_name");     // may be null if not requested
            display = (String) a.get("name");          // usually present
            String id = (String) a.get("id");
            // username: FB usually has no username; build one that stays stable
            username = "fb_" + (id != null ? id : UUID.randomUUID().toString().substring(0, 12));
        } else {
            // Fallback for any other providers you add later
            display = (String) a.getOrDefault("name", "User");
            email   = (String) a.getOrDefault("email", null);
            username = "oauth_" + UUID.randomUUID().toString().substring(0, 12);
        }

        // --- Make sure we have a non-empty display name ---
        if (display == null || display.isBlank()) {
            if (first != null || last != null) display = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
        }
        if (display == null || display.isBlank()) display = username;

        // --- Create or update the local user ---
        User user = userService.createOAuthUser(
                email,
                first,
                last,
                display,
                provider.toUpperCase(),  // GOOGLE | FACEBOOK
                username
        );

        // Send welcome email on first sign-in
        if (user.getLastLoginAt() == null) {
            try { emailService.sendWelcomeEmail(user, baseUrl); } catch (Exception ignored) {}
        }

        // Touch last login timestamp
        userService.touchLastLogin(user.getId());

        // Put a very small DTO in HTTP session for Thymeleaf nav, etc.
        request.getSession(true).setAttribute("USER", user);

        // Done
        response.sendRedirect("/");
    }
}
