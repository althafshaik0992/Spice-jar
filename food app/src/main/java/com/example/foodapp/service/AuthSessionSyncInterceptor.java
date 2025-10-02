// src/main/java/.../web/AuthSessionSyncInterceptor.java
package com.example.foodapp.service;

import com.example.foodapp.model.User;
import com.example.foodapp.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.servlet.HandlerInterceptor;

public class AuthSessionSyncInterceptor implements HandlerInterceptor {

    private final UserService userService;

    public AuthSessionSyncInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("USER") == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                String email = null;
                if (auth.getPrincipal() instanceof org.springframework.security.core.userdetails.User u) {
                    email = u.getUsername();
                } else if (auth.getPrincipal() instanceof OAuth2User o) {
                    Object e = o.getAttributes().get("email");
                    if (e != null) email = e.toString();
                } else if (auth.getPrincipal() instanceof String s && !"anonymousUser".equals(s)) {
                    email = s;
                }
                if (email != null) {
                    userService.findByEmail(email).ifPresent(u -> session.setAttribute("USER", u));
                }
            }
        }
        return true;
    }
}
