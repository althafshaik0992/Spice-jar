// src/main/java/com/example/foodapp/controller/ForgotPasswordController.java
package com.example.foodapp.controller;

import com.example.foodapp.model.PasswordResetToken;
import com.example.foodapp.model.User;
import com.example.foodapp.service.EmailService;
import com.example.foodapp.service.PasswordResetService;
import com.example.foodapp.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class ForgotPasswordController {

    private final UserService userService;
    private final PasswordResetService resetService;
    private final EmailService emailService;

    // Show request form (support both routes)
    @GetMapping({"/forgotPassword", "/forgot-password"})
    public String forgotForm() {
        return "forgot_password";
    }

    // Handle request: generate token and email link
    @PostMapping({"/forgotPassword", "/forgot-password"})
    public String sendLink(@RequestParam String email,
                           HttpServletRequest req,
                           RedirectAttributes ra) {
        Optional<User> userOpt = userService.findByEmail(email);
        // Always respond with success to avoid email enumeration
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            PasswordResetToken t = resetService.createToken(user);

            String base = getBaseUrl(req); // http://localhost:8080
            String resetUrl = base + "/reset-password?token=" + t.getToken();
            emailService.sendResetPasswordEmail(user.getEmail(), resetUrl);
        }
        ra.addFlashAttribute("ok", true);
        return "redirect:/forgotPassword";
    }

    @GetMapping("/reset-password")
    public String showReset(@RequestParam String token, Model model) {
        boolean valid = resetService.validate(token); // returns true/false
        model.addAttribute("invalid", !valid);
        model.addAttribute("token", token);
        return "reset_password";
    }


    // Handle password update
    @PostMapping("/reset-password")
    public String doReset(
            @RequestParam String token,
            @RequestParam String password,
            @RequestParam String confirm,
            Model model) {

        if (!password.equals(confirm)) {
            model.addAttribute("invalid", false);
            model.addAttribute("token", token);
            model.addAttribute("error", "Passwords do not match.");
            return "reset_password";
        }

        var result = resetService.resetPassword(token, password);
        if (!result.ok()) {
            model.addAttribute("invalid", !result.tokenValid());
            model.addAttribute("token", token);
            model.addAttribute("error", result.message());
            return "reset_password";
        }

        return "redirect:/login?reset=success";
    }




    private static String getBaseUrl(HttpServletRequest req) {
        String scheme = req.getScheme();             // http
        String serverName = req.getServerName();     // localhost
        int serverPort = req.getServerPort();        // 8080
        String contextPath = req.getContextPath();   // ""
        String port = (serverPort == 80 || serverPort == 443) ? "" : ":" + serverPort;
        return scheme + "://" + serverName + port + contextPath;
    }
}
