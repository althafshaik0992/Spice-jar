package com.example.foodapp.service;

import com.example.foodapp.model.ContactForm;
import com.example.foodapp.repository.ContactMessageRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

// --- FIX: Updated imports to Jakarta Mail ---
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
// The old javax.mail.Authenticator is replaced by a simple Jakarta Authenticator

import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.Date;
import java.util.Properties;
import java.util.UUID;

@Service
public class ContactService {

    // === SMTP + routing, read from application.properties ===
    @Value("${mail.username}")
    private String username;     // your fixed SMTP auth user (e.g., Gmail address)
    @Value("${mail.password}")
    private String password;     // app password or SMTP password
    @Value("${mail.supportTo}")
    private String supportTo;    // where tickets go (e.g., support@yourdomain)
    @Value("${mail.fromName:The Spice Jar}")
    private String fromName;     // display name for From:

    @Value("${mail.smtp.host:smtp.gmail.com}")
    private String host;

    @Value("${mail.smtp.port:587}")
    private int port;

    @Value("${mail.smtp.starttls:true}")
    private boolean starttls;

    @Value("${mail.smtp.auth:true}")
    private boolean auth;

    private final SpringTemplateEngine thymeleaf;
    private final ContactMessageRepository contactRepo;

    public ContactService(SpringTemplateEngine thymeleaf,
                          ContactMessageRepository contactRepo) {
        this.thymeleaf = thymeleaf;
        this.contactRepo = contactRepo;
    }

    /**
     * Saves the contact to DB and sends a branded HTML ticket to your support inbox.
     * Uses Thymeleaf template: templates/email/contact_ticket.html
     */
    public String sendContact(ContactForm form) throws Exception {

        String ticketId = "SJ-" + java.util.UUID.randomUUID().toString()
                .substring(0, 8).toUpperCase();

        form.setTicketId(ticketId);
        form.setStatus("OPEN");

        contactRepo.save(form);

        Context ctx = new Context();
        ctx.setVariable("form", form);
        ctx.setVariable("ticketId", ticketId);
        ctx.setVariable("year", java.time.Year.now().getValue());

        String html = thymeleaf.process("contact_ticket", ctx);

        Session session = Session.getInstance(smtpProps(), new jakarta.mail.Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        MimeMessage msg = new MimeMessage(session);
        msg.setSentDate(new java.util.Date());
        msg.setFrom(new InternetAddress(username, fromName, java.nio.charset.StandardCharsets.UTF_8.name()));

        if (form.getEmail() != null && !form.getEmail().isBlank()) {
            msg.setReplyTo(new Address[]{ new InternetAddress(form.getEmail(), true) });
        }

        msg.setRecipient(Message.RecipientType.TO, new InternetAddress(supportTo));
        msg.setSubject("[Ticket " + ticketId + "] " + safe(form.getSubject()) + " (" + safe(form.getTopic()) + ")",
                java.nio.charset.StandardCharsets.UTF_8.name());
        msg.setContent(html, "text/html; charset=UTF-8");

        Transport.send(msg);

        return ticketId;
    }


    // ---------- helpers ----------

    private Properties smtpProps() {
        Properties p = new Properties();
        p.put("mail.transport.protocol", "smtp");
        p.put("mail.smtp.host", host);
        p.put("mail.smtp.port", String.valueOf(port));
        p.put("mail.smtp.auth", String.valueOf(auth));
        p.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        // FIX: Set the Jakarta-specific handler property to avoid ClassCastException
        p.put("mail.mime.contenthandler", "com.sun.mail.handlers.text_html");
        return p;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
