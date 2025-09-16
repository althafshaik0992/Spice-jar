//package com.spicejar.service;
//
//import com.example.foodapp.model.Order;
//import jakarta.mail.Address;
//import jakarta.mail.Message;
//import jakarta.mail.Session;
//import jakarta.mail.Transport;
//import jakarta.mail.internet.InternetAddress;
//import jakarta.mail.internet.MimeMessage;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//import org.thymeleaf.context.Context;
//import org.thymeleaf.spring6.SpringTemplateEngine;
//
//import java.nio.charset.StandardCharsets;
//import java.time.Year;
//import java.time.format.DateTimeFormatter;
//import java.util.Map;
//import java.util.Properties;
//import java.util.UUID;
//
//@Service
//public class OrderEmailService {
//
//    @Autowired
//    private SpringTemplateEngine templateEngine;
//
//    // === SMTP/auth (same keys you used in ContactService) ===
//    @Value("${mail.host}")
//    private String host;
//    @Value("${mail.port}")
//    private int port;
//    @Value("${mail.auth}")
//    private boolean auth;
//    @Value("${mail.starttls.enable}")
//    private boolean starttls;
//    @Value("${mail.username}")
//    private String username;
//    @Value("${mail.password}")
//    private String password;
//
//    // optional branded from name
//    @Value("${mail.fromName:The Spice Jar}")
//    private String fromName;
//    @Value("${mail.fromAddr:no-reply@localhost}")
//    private String fromAddr;
//    @Value("${mail.orders.bcc:}")
//    private String ordersBcc;
//
//    /**
//     * Sends an order confirmation email to the customer.
//     * @param order The Order object containing all the details.
//     */
//    public void sendOrderConfirmationEmail(Order order) {
//        if (!nonEmpty(order.getEmail())) {
//            // No email address, cannot send confirmation
//            return;
//        }
//
//        try {
//            String subject = "Your Order is Confirmed! #" + order.getId();
//            String preheader = "Thanks for your order — it’s on the way!";
//
//            // 1. Prepare Thymeleaf context
//            Context ctx = new Context();
//            ctx.setVariable("order", order);
//            ctx.setVariable("subject", subject);
//            ctx.setVariable("preheader", preheader);
//            ctx.setVariable("firstName", order.getCustomerName());
//            ctx.setVariable("orderNumber", order.getId());
//            ctx.setVariable("status", order.getStatus());
//            ctx.setVariable("orderDate", order.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
//            ctx.setVariable("shippingAddress", String.format("%s, %s, %s %s", order.getStreet(), order.getCity(), order.getState(), order.getZip()));
//            ctx.setVariable("items", order.getItems());
//            ctx.setVariable("subtotal", String.format("$%.2f", order.getSubtotal()));
//            ctx.setVariable("tax", String.format("$%.2f", order.getTax()));
//            ctx.setVariable("total", String.format("$%.2f", order.getGrandTotal()));
//
//            // Mocks for template variables
//            ctx.setVariable("links", Map.of(
//                    "home", "/",
//                    "orders", "/orders",
//                    "help", "/help",
//                    "unsub", "/unsubscribe"
//            ));
//            ctx.setVariable("assets", Map.of(
//                    "logo", "/images/spice-jar-logo.png"
//            ));
//            ctx.setVariable("year", Year.now().getValue());
//
//            // 2. Process Thymeleaf template to get HTML content
//            String html = templateEngine.process("order_confirmation", ctx);
//
//            // 3. Build and send email
//            Session mailSession = Session.getInstance(smtpProps());
//            MimeMessage msg = new MimeMessage(mailSession);
//
//            msg.setFrom(new InternetAddress(fromAddr, fromName));
//
//            // TO = customer
//            msg.setRecipient(Message.RecipientType.TO, new InternetAddress(order.getEmail()));
//            // Optional BCC (split by comma)
//            if (nonEmpty(ordersBcc)) {
//                for (String bcc : ordersBcc.split(",")) {
//                    if (nonEmpty(bcc)) {
//                        msg.addRecipient(Message.RecipientType.BCC, new InternetAddress(bcc.trim()));
//                    }
//                }
//            }
//
//            msg.setSubject("Order Confirmed • " + order.getId(), StandardCharsets.UTF_8.name());
//            msg.setContent(html, "text/html; charset=UTF-8");
//
//            Transport.send(msg);
//
//        } catch (Exception e) {
//            // You may want to handle this more gracefully
//            e.printStackTrace();
//        }
//    }
//
//    // ---------- helpers ----------
//    private Properties smtpProps() {
//        Properties p = new Properties();
//        p.put("mail.transport.protocol", "smtp");
//        p.put("mail.smtp.host", host);
//        p.put("mail.smtp.port", String.valueOf(port));
//        p.put("mail.smtp.auth", String.valueOf(auth));
//        p.put("mail.smtp.starttls.enable", String.valueOf(starttls));
//        // helps some environments with HTML handler
//        p.put("mail.mime.contenthandler", "com.sun.mail.handlers.text_html");
//        return p;
//    }
//
//    private boolean nonEmpty(String s) {
//        return s != null && !s.trim().isEmpty();
//    }
//}
