// src/main/java/com/example/foodapp/security/LoginSuccessHandler.java
package com.example.foodapp.service;

import com.example.foodapp.model.User;
import com.example.foodapp.web.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final RedirectStrategy redirect = new DefaultRedirectStrategy();

    public LoginSuccessHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {

        // 1) Load your domain user by username
        String username = ((UserDetails) authentication.getPrincipal()).getUsername();
        User u = userService.findByUsername(username)
                .orElseThrow(); // Should exist if authentication succeeded

        // 2) Put lean object in HTTP session for your controllers/templates
        HttpSession session = request.getSession(true);
        session.setAttribute("USER", new SessionUser(
                u.getId(), u.getFirstName(), u.getLastName(), u.getEmail()
        ));

        request.getSession(true).setAttribute("USER", userService.getCurrentUser(session));

        userService.touchLastLogin(u.getId());

        // 3) Return to originally requested page, or home
        try {
            SavedRequest saved = new HttpSessionRequestCache().getRequest(request, response);
            if (saved != null) {
                redirect.sendRedirect(request, response, saved.getRedirectUrl());
            } else {
                redirect.sendRedirect(request, response, "/");
            }
        } catch (Exception ignored) {}
    }
}
