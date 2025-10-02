package com.example.foodapp.controller;

import com.example.foodapp.model.User;
import com.example.foodapp.service.EmailService;
import com.example.foodapp.service.EmailServiceWelcome;
import com.example.foodapp.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping // optional, keeps routes as-is
public class UserauthController {

    private final UserService userService;

    private final EmailServiceWelcome emailService;

    public UserauthController(UserService userService, EmailServiceWelcome emailService) {
        this.userService = userService;
        this.emailService = emailService;
    }

    // ---- LOGIN (USER) ----
    @GetMapping("/login")
    public String loginPage(@RequestParam(name = "return", required = false) String returnUrl,
                            Model m) {
        m.addAttribute("returnUrl", returnUrl);   // can be null
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        @RequestParam(name = "return", required = false) String returnUrl,
                        HttpSession session,
                        Model m) {
        var opt = userService.findByUsername(username);
        if (opt.isPresent() && userService.checkPassword(password, opt.get().getPassword())) {
            session.setAttribute("USER", opt.get());
            // ✅ if a return target exists, go there; else go home
            if (returnUrl != null && !returnUrl.isBlank()) {
                return "redirect:" + returnUrl;
            }
            return "redirect:/";
        }
        m.addAttribute("error", "Invalid username or password");
        m.addAttribute("returnUrl", returnUrl); // preserve intended target on error
        return "login";
    }

    // ---- REGISTER (USER) ----
    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String registerSubmit(@RequestParam String username,
                                 @RequestParam String password,
                                 @RequestParam String firstName,
                                 @RequestParam String lastName,
                                 @RequestParam String address,
                                 @RequestParam String email,
                                 @RequestParam String phone,
                                 Model m,
                                 HttpSession session) {
        if (userService.findByUsername(username).isPresent()) {
            m.addAttribute("error", "User already exists");
            return "register";
        }
        User u = userService.register(firstName, lastName, address, email, phone, username, password);
        session.setAttribute("USER", u);

        return "login";
    }

    // ---- LOGOUT ----
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }


    // src/main/java/com/example/foodapp/controller/AccountController.java
    @PostMapping("/account/welcome-email")
    public String resendWelcome(HttpSession session, RedirectAttributes ra,
                                @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        Object su = session.getAttribute("USER");
        if (su == null) return "redirect:/login";
        Long userId = extractUserId(su);
        var user = userService.findById(userId).orElse(null);
        if (user != null) {
            try { emailService.sendWelcomeEmail(user, baseUrl); } catch (Exception ignore) {}
            ra.addFlashAttribute("ok", true);
        }
        return "redirect:/account";
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
