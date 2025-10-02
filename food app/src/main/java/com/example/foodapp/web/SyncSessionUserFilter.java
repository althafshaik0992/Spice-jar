package com.example.foodapp.web;

import com.example.foodapp.model.User;
import com.example.foodapp.service.UserService;
import com.example.foodapp.web.SessionUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class SyncSessionUserFilter extends OncePerRequestFilter {

    private final UserService userService;

    public SyncSessionUserFilter(UserService userService) {
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("USER") == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                try {
                    User u = userService.getCurrentUser(req.getSession(true)); // or userService.getCurrentUser()
                    if (u != null) {
                        session.setAttribute("USER", new SessionUser(
                                u.getId(), u.getFirstName(), u.getLastName(), u.getEmail()
                        ));
                    }
                } catch (Throwable ignore) {
                    // don’t block the request if this fails
                }
            }
        }
        chain.doFilter(req, res);
    }
}
