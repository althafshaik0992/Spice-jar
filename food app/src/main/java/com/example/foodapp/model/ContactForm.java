package com.example.foodapp.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Entity
public class ContactForm {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        // ✅ Ticket info
        @Column(nullable = false, unique = true, length = 20)
        private String ticketId;

        @Column(nullable = false, length = 20)
        private String status = "OPEN"; // OPEN | IN_PROGRESS | CLOSED

        @Column(nullable = false)
        private LocalDateTime createdAt = LocalDateTime.now();

        // ✅ Admin notes (internal)
        @Column(length = 5000)
        private String adminNotes;

        // Optional: link ticket to order
        @Size(max = 40)
        private String orderId;

        @NotBlank @Size(max = 80)
        private String name;

        @NotBlank @Email @Size(max = 120)
        private String email;

        @Size(max = 30)
        private String phone;

        @Size(max = 24)
        private String topic = "GENERAL";

        @NotBlank @Size(max = 120)
        private String subject;

        @NotBlank @Size(max = 5000)
        private String message;

        public ContactForm() {}

        // ---- getters/setters ----

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getTicketId() { return ticketId; }
        public void setTicketId(String ticketId) { this.ticketId = ticketId; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public String getAdminNotes() { return adminNotes; }
        public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }

        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }

        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }

        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
}
