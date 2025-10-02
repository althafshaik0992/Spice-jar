// src/main/java/com/example/foodapp/security/OAuth2LoginSuccessHandler.java
package com.example.foodapp.service;

import com.example.foodapp.model.User;
import com.example.foodapp.web.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.Map;

public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final EmailServiceWelcome emailService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public OAuth2LoginSuccessHandler(UserService userService, EmailServiceWelcome emailService) {
        this.userService = userService;
        this.emailService = emailService;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        OAuth2User oauth = (OAuth2User) authentication.getPrincipal();
        Map<String, Object> a = oauth.getAttributes();

        // Google/OpenID standard claims
        String email  = (String) a.getOrDefault("email", "");
        String given  = (String) a.getOrDefault("given_name", null);
        String family = (String) a.getOrDefault("family_name", null);
        String name   = (String) a.getOrDefault("name", null);

        // Create or fetch user
        User user = userService.createOAuthUser(email, given, family, name);

        // Welcome email only on first sign-in
        if (user.getLastLoginAt() == null) {
            try { emailService.sendWelcomeEmail(user, baseUrl); } catch (Exception ignored) {}
        }

        // Put lean session object for controllers/Thymeleaf
        request.getSession(true).setAttribute("USER", user);

        // Touch last login
        userService.touchLastLogin(user.getId());

        // Go home (or replace with SavedRequest redirect if you prefer)
        response.sendRedirect("/");
    }
}
