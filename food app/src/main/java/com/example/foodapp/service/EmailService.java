// src/main/java/com/example/foodapp/service/EmailService.java
package com.example.foodapp.service;

import com.example.foodapp.model.Order;
import com.example.foodapp.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.internet.MimeMessage;
// EmailService.java

// EmailService.java (diff-style)
import org.springframework.beans.factory.annotation.Value;
// ...

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final OrderRepository orderRepository;

    @Value("${app.base-url}")
    private String baseUrl; // e.g. http://localhost:8080/

    public void sendOrderConfirmation(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        if (order.getEmail() == null || order.getEmail().isBlank()) return;

        // Build absolute links for email (no @{...} in templates)
        String orderUrl   = baseUrl + "orders/" + order.getId();
        String invoiceUrl = baseUrl + "orders/" + order.getId() + "/invoice.pdf";

        Context ctx = new Context();
        ctx.setVariable("order", order);
        ctx.setVariable("subject", "Your order #" + order.getId() + " at The Spice Jar");
        ctx.setVariable("preheader", "Thanks—your order is confirmed!");
        ctx.setVariable("year", java.time.Year.now().getValue());
        ctx.setVariable("orderUrl", orderUrl);
        ctx.setVariable("invoiceUrl", invoiceUrl);

        String html = templateEngine.process("order_confirmation", ctx);
        sendHtml(order.getEmail(), "The Spice Jar • Order #" + order.getId(), html);
    }

    // sendHtml(...) stays the same


    private void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setFrom("no-reply@spicejar.example"); // change to your verified sender
            helper.setText(html, true);
            mailSender.send(msg);
        } catch (Exception e) {
            throw new RuntimeException("Email send failed: " + e.getMessage(), e);
        }
    }

    public void sendResetPasswordEmail(String toEmail, String resetUrl) {
        org.thymeleaf.context.Context ctx = new org.thymeleaf.context.Context();
        ctx.setVariable("resetUrl", resetUrl);
        ctx.setVariable("subject", "Reset your Spice Jar password");
        String html = templateEngine.process("reset_password_email", ctx);
        sendHtml(toEmail, "Reset your password", html);
    }
}