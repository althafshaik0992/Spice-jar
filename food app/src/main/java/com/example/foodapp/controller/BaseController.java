// src/main/java/com/example/foodapp/controller/BaseController.java
package com.example.foodapp.controller;

import com.example.foodapp.model.User;
import com.example.foodapp.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;

/** Common helpers for all MVC controllers */
public abstract class BaseController {

    @Autowired
    private UserService userService;

    /**
     * Preferred user lookup. Tries UserService (e.g. Spring Security principal),
     * then falls back to the legacy "USER" session attribute.
     */
    protected User currentUser(HttpSession session) {
        try {
            User u = userService.getCurrentUser(session); // ok if null / not implemented
            if (u != null) return u;
        } catch (Throwable ignored) { /* fallback */ }

        Object s = (session != null) ? session.getAttribute("USER") : null;
        return (s instanceof User) ? (User) s : null;
    }
}
