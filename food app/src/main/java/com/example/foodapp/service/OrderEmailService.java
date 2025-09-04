//// src/main/java/com/example/foodapp/service/OrderEmailService.java
//package com.example.foodapp.service;
//
//import com.example.foodapp.model.Order;
//import com.example.foodapp.model.OrderItem;
//import com.example.foodapp.model.User;
//
//import jakarta.mail.Address;
//import jakarta.mail.Message;
//import jakarta.mail.PasswordAuthentication;
//import jakarta.mail.Session;
//import jakarta.mail.Transport;
//import jakarta.mail.internet.InternetAddress;
//import jakarta.mail.internet.MimeMessage;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//import org.thymeleaf.context.Context;
//import org.thymeleaf.spring6.SpringTemplateEngine;
//
//import java.math.BigDecimal;
//import java.nio.charset.StandardCharsets;
//import java.time.Year;
//import java.time.format.DateTimeFormatter;
//import java.util.*;
//
//@Service
//public class OrderEmailService {
//
//    // === SMTP/auth (same keys you used in ContactService) ===
//    @Value("${mail.username}")
//    private String username;
//
//    @Value("${mail.password}")
//    private String password;
//
//    // optional branded from name
//    @Value("${mail.fromName:The Spice Jar}")
//    private String fromName;
//
//    // optional BCC for your records (comma-separated). Can be blank.
//    @Value("${mail.orders.bcc:}")
//    private String ordersBcc;
//
//    // SMTP details with sensible defaults for Gmail
//    @Value("${mail.smtp.host:smtp.gmail.com}")
//    private String host;
//
//    @Value("${mail.smtp.port:587}")
//    private int port;
//
//    @Value("${mail.smtp.starttls:true}")
//    private boolean starttls;
//
//    @Value("${mail.smtp.auth:true}")
//    private boolean auth;
//
//    private final SpringTemplateEngine thymeleaf;
//
//    public OrderEmailService(SpringTemplateEngine thymeleaf) {
//        this.thymeleaf = thymeleaf;
//    }
//
//    /**
//     * Renders templates/email/order_confirmation.html and sends it to the customer.
//     */
//    public void sendOrderConfirmation(User user, Order order) throws Exception {
//        // ---- Build template model ----
//        String firstName = nonEmpty(user.getFirstName()) ? user.getFirstName()
//                : (nonEmpty(user.getUsername()) ? user.getUsername() : "friend");
//
//        String orderNumber = "#" + order.getId();
//        String orderDate = order.getCreatedAt() != null
//                ? order.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a"))
//                : "";
//
//        String status = nonEmpty(order.getStatus()) ? order.getStatus() : "PAID";
//
//        // Items -> simple String map rows for email safety
//        List<Map<String, String>> items = new ArrayList<>();
//        if (order.getItems() != null) {
//            for (OrderItem it : order.getItems()) {
//                int qty = it.getQuantity() == null ? 0 : it.getQuantity();
//                BigDecimal price = it.getPrice() == null ? BigDecimal.ZERO : it.getPrice();
//                BigDecimal line = price.multiply(BigDecimal.valueOf(qty));
//
//                items.add(Map.of(
//                        "name", nonNull(it.getProductName()),
//                        "qty", String.valueOf(qty),
//                        "price", price.toPlainString(),
//                        "lineTotal", line.toPlainString()
//                ));
//            }
//        }
//
//        // Money fields (adapt if your entity already stores tax/grand total)
//        BigDecimal subtotal = nz(order.getTotal());
//        BigDecimal tax = nz(order.getTax());
//        BigDecimal grandTotal = order.getGrandTotal() != null ? order.getGrandTotal() : subtotal.add(tax);
//
//        // Links & assets (replace with your real URLs/CDN)
//        Map<String, String> links = Map.of(
//                "home",   "https://your-domain.example/",
//                "orders", "https://your-domain.example/orders/" + order.getId(),
//                "help",   "https://your-domain.example/help",
//                "unsub",  "https://your-domain.example/unsubscribe"
//        );
//        Map<String, String> assets = Map.of(
//                "logo", "https://your-cdn.example/spicejar-logo.png"
//        );
//
//        // Thymeleaf context
//        Context ctx = new Context();
//        ctx.setVariable("subject", "Order Confirmed • " + orderNumber);
//        ctx.setVariable("preheader", "Thanks for your order — it’s on the way!");
//        ctx.setVariable("firstName", firstName);
//        ctx.setVariable("orderNumber", orderNumber);
//        ctx.setVariable("orderDate", orderDate);
//        ctx.setVariable("status", status);
//        ctx.setVariable("items", items);
//        ctx.setVariable("subtotal", subtotal.toPlainString());
//        ctx.setVariable("tax", tax.toPlainString());
//        ctx.setVariable("total", grandTotal.toPlainString());
//        ctx.setVariable("shippingAddress", nonNull(order.getAddress()));
//        ctx.setVariable("year", Year.now().getValue());
//        ctx.setVariable("links", links);
//        ctx.setVariable("assets", assets);
//
//        // Template path: src/main/resources/templates/email/order_confirmation.html
//        String html = thymeleaf.process("email/order_confirmation", ctx);
//
//        // ---- Build & send (Jakarta Mail, like your ContactService) ----
//        Session session = Session.getInstance(smtpProps(), new jakarta.mail.Authenticator() {
//            @Override
//            protected PasswordAuthentication getPasswordAuthentication() {
//                return new PasswordAuthentication(username, password);
//            }
//        });
//
//        MimeMessage msg = new MimeMessage(session);
//        msg.setSentDate(new Date());
//
//        // Send FROM your authenticated mailbox
//        msg.setFrom(new InternetAddress(username, fromName, StandardCharsets.UTF_8.name()));
//
//        // Reply-To the store mailbox (or customer if you want replies to go to them)
//        if (nonEmpty(user.getEmail())) {
//            Address[] reply = { new InternetAddress(user.getEmail()) };
//            msg.setReplyTo(reply);
//        }
//
//        // TO = customer
//        msg.setRecipient(Message.RecipientType.TO, new InternetAddress(user.getEmail()));
//        // Optional BCC (split by comma)
//        if (nonEmpty(ordersBcc)) {
//            for (String bcc : ordersBcc.split(",")) {
//                if (nonEmpty(bcc)) {
//                    msg.addRecipient(Message.RecipientType.BCC, new InternetAddress(bcc.trim()));
//                }
//            }
//        }
//
//        msg.setSubject("Order Confirmed • " + orderNumber, StandardCharsets.UTF_8.name());
//        msg.setContent(html, "text/html; charset=UTF-8");
//
//        Transport.send(msg);
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
//    private static String nonNull(String s) { return s == null ? "" : s; }
//    private static boolean nonEmpty(String s) { return s != null && !s.isBlank(); }
//    private static BigDecimal nz(BigDecimal b) { return b == null ? BigDecimal.ZERO : b; }
//}
