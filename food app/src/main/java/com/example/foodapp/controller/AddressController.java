// src/main/java/com/example/foodapp/controller/AddressController.java
package com.example.foodapp.controller;

import com.example.foodapp.model.Address;
import com.example.foodapp.model.User;
import com.example.foodapp.service.AddressService;
import com.example.foodapp.service.EmailServiceWelcome;
import com.example.foodapp.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/addresses")
public class AddressController {

    private final AddressService addressService;
    private final UserService userService;
    private final EmailServiceWelcome emailService;

    public AddressController(AddressService addressService, UserService userService, EmailServiceWelcome emailService) {
        this.addressService = addressService;
        this.userService = userService;
        this.emailService = emailService;
    }

    // ===== Helpers ============================================================

    /** Try to get the current user from UserService (preferred) then session. */
    private User currentUser(HttpSession session) {
        try {
            // if your UserService has this overload, it will work;
            // if not, the catch will fall back to session.
            User u = userService.getCurrentUser(session);
            if (u != null) return u;
        } catch (Throwable ignore) { /* fall back */ }

        Object s = session != null ? session.getAttribute("USER") : null;
        return (s instanceof User) ? (User) s : null;
    }

    /** Returns true if the address belongs to the given user (null-safe). */
    private boolean ownedBy(Address a, User u) {
        if (a == null || u == null || a.getUser() == null) return false;
        return u.getId() != null && u.getId().equals(a.getUser().getId());
    }

    // ===== Routes =============================================================

    @GetMapping
    public String list(Model model, HttpSession session) {
        User user = currentUser(session);
        if (user == null) return "redirect:/login";

        model.addAttribute("user", user);
        model.addAttribute("addresses", addressService.listForUser(user));
        return "addresses";
    }

    @GetMapping("/new")
    public String create(Model model, HttpSession session) {
        User user = currentUser(session);
        if (user == null) return "redirect:/login";

        model.addAttribute("user", user);
        model.addAttribute("address", new Address());
        return "address_form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model, HttpSession session) {
        User user = currentUser(session);
        if (user == null) return "redirect:/login";

        Address a = addressService.get(id);
        if (!ownedBy(a, user)) {
            // silently ignore or show 404/403 page if you have one
            return "redirect:/addresses";
        }

        model.addAttribute("user", user);
        model.addAttribute("address", a);
        return "address_form";
    }

    @PostMapping
    public String save(@ModelAttribute Address address, HttpSession session) {
        User user = currentUser(session);
        if (user == null) return "redirect:/login";

        // Ensure service receives the logged-in user
        addressService.save(address, user);
        return "redirect:/addresses";
    }

    @PostMapping("/{id}/default")
    public String makeDefault(@PathVariable Long id, HttpSession session) {
        User user = currentUser(session);
        if (user == null) return "redirect:/login";

        Address a = addressService.get(id);
        if (!ownedBy(a, user)) return "redirect:/addresses";

        addressService.makeDefault(a, user);
        return "redirect:/addresses";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, HttpSession session) {
        User user = currentUser(session);
        if (user == null) return "redirect:/login";

        Address a = addressService.get(id);
        if (!ownedBy(a, user)) return "redirect:/addresses";

        addressService.delete(a);
        return "redirect:/addresses";
    }






    private Long extractUserId(Object sessionUser) {
        try {
            var m = sessionUser.getClass().getMethod("getId");
            Object id = m.invoke(sessionUser);
            if (id instanceof Number) return ((Number) id).longValue();
        } catch (Exception ignore) {
        }
        return null;
    }
}
