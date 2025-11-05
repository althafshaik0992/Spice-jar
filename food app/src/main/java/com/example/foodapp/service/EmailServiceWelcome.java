package com.example.foodapp.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;

@Service
@Component
public class EmailServiceWelcome {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    public EmailServiceWelcome(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    // ... your other methods (sendHtml, sendOrderConfirmation, etc.)

    public void sendWelcomeEmail(com.example.foodapp.model.User user, String baseUrl) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) return;

        var ctx = new org.thymeleaf.context.Context();
        ctx.setVariable("subject", "Welcome to The Spice Jar, " + safe(user.getFirstName(), "friend") + "!");
        ctx.setVariable("preheader", "Your account is ready—let’s get cooking.");
        ctx.setVariable("firstName", safe(user.getFirstName(), user.getDisplayName()));
        ctx.setVariable("year", java.time.Year.now().getValue());

        // Helpful links
        ctx.setVariable("homeUrl", baseUrl + "/");
        ctx.setVariable("menuUrl", baseUrl + "/menu");
        ctx.setVariable("accountUrl", baseUrl + "/account");
        ctx.setVariable("helpUrl", baseUrl + "/contact");

        String html = templateEngine.process("email-welcome", ctx);
        sendHtml(user.getEmail(), "Welcome to The Spice Jar", html);
    }

    private static String safe(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v;
    }

    private void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            h.setTo(to);
            h.setSubject(subject);
            h.setFrom("no-reply@spicejar.example"); // set yours
            h.setText(html, true);
            mailSender.send(msg);
        } catch (Exception e) {
            throw new RuntimeException("Email send failed", e);
        }
    }
}
