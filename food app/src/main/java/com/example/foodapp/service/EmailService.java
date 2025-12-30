package com.example.foodapp.service;



import com.example.foodapp.model.GiftCard;
import com.example.foodapp.model.Order;
import com.example.foodapp.model.OrderItem;
import com.example.foodapp.model.Payment;
import com.example.foodapp.repository.OrderRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final OrderRepository orderRepository;

    @Value("${app.base-url}")
    private String baseUrl; // e.g. http://localhost:8080/

    /* ===================== ORDER CONFIRMATION ===================== */

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

    /* ===================== GENERIC HTML SENDER ===================== */

    private void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    msg,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    "UTF-8"
            );
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setFrom("no-reply@spicejar.example"); // TODO: change to your verified sender
            helper.setText(html, true);
            mailSender.send(msg);
        } catch (Exception e) {
            throw new RuntimeException("Email send failed: " + e.getMessage(), e);
        }
    }

    /* ===================== RESET PASSWORD EMAIL ===================== */

    public void sendResetPasswordEmail(String toEmail, String resetUrl) {
        Context ctx = new Context();
        ctx.setVariable("resetUrl", resetUrl);
        ctx.setVariable("subject", "Reset your Spice Jar password");
        String html = templateEngine.process("reset_password_email", ctx);
        sendHtml(toEmail, "Reset your password", html);
    }

    /* ===================== GENERIC TEMPLATE EMAIL ===================== */

    public void sendTemplate(String to, String subject, String template, Map<String, Object> model) {
        try {
            Context ctx = new Context();
            ctx.setVariables(model);
            String html = templateEngine.process(template, ctx);
            sendHtml(to, subject, html);
        } catch (Exception e) {
            throw new RuntimeException("Email failed: " + e.getMessage(), e);
        }
    }

    /* ===================== GIFT CARD EMAIL ===================== */

// EmailService.java

    public void sendGiftCardEmail(String toEmail, String buyerName, GiftCard card) {
        if (toEmail == null || toEmail.isBlank()) return;

        String subject = "Your SpiceJar Gift Card – " + card.getCode();

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, yyyy");

        Map<String, Object> model = new HashMap<>();
        model.put("subject", subject);
        model.put("buyerName", buyerName);
        model.put("code", card.getCode());
        model.put("amount",
                card.getOriginalAmount() != null
                        ? card.getOriginalAmount().setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        );
        model.put("status", card.isActive() ? "Active" : "Inactive");
        model.put("expiresOn",
                card.getExpiresAt() != null ? card.getExpiresAt().format(fmt) : null
        );

        // for links & images
        // baseUrl should be something like "https://localhost:8080/" or "https://spicejar.com/"
        model.put("siteUrl", baseUrl);
        model.put("giftCardsUrl", baseUrl + "gift-cards");
        model.put("supportUrl", baseUrl + "contact");
        model.put("logoUrl", baseUrl + "images/logo-circle.png");

        // NEW: absolute URL for the gift card image used in the email template
        model.put("giftImageUrl", baseUrl + "images/spicejar-giftcard.png");

        model.put("year", java.time.Year.now().getValue());

        // template name must match the file path (without .html)
        sendTemplate(toEmail, subject, "email/gift_card_email", model);
    }

    public void sendOrderSurveyEmail(Order order) {
        if (order == null || order.getEmail() == null || order.getEmail().isBlank()) return;

        String subject = "How was your SpiceJar order " + order.getConfirmationNumber() + "?";

        List<OrderItem> surveyItems = order.getItems().stream()
                .filter(Objects::nonNull)
                .filter(it -> {
                    Long pid = it.getProductId();
                    String name = it.getProductName() != null ? it.getProductName() : "";
                    boolean looksLikeGiftCard =
                            name.toLowerCase(Locale.ROOT).contains("gift card") ||
                                    name.toLowerCase(Locale.ROOT).contains("giftcard");
                    return pid != null && pid > 0 && !looksLikeGiftCard;
                })
                .toList();

        Map<String, Object> model = new HashMap<>();
        model.put("subject", subject);
        model.put("customerName", order.getCustomerName());
        model.put("order", order);
        model.put("surveyItems", surveyItems);
        model.put("brandLogoUrl", baseUrl + "images/logo-circle.png");
        model.put("siteUrl", baseUrl);
        model.put("reviewBaseUrl", baseUrl + "product/");
        model.put("year", java.time.Year.now().getValue());

        sendTemplate(order.getEmail(), subject, "email/order_survey", model);
    }

    public void sendRefundConfirmation(Order order, Payment refund) {
        if (order == null || order.getEmail() == null || order.getEmail().isBlank()) return;
        if (refund == null) return;

        String subject = "Refund confirmed • " + order.getConfirmationNumber();

        Map<String, Object> model = new HashMap<>();
        model.put("subject", subject);
        model.put("customerName", order.getCustomerName());
        model.put("order", order);
        model.put("refund", refund);
        model.put("brandLogoUrl", baseUrl + "images/logo-circle.png");
        model.put("siteUrl", baseUrl);
        model.put("year", java.time.Year.now().getValue());

        sendTemplate(order.getEmail(), subject, "email/refund_confirmation", model);
    }




}
