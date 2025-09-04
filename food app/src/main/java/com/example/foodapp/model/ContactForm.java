package com.example.foodapp.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;


@Entity
public class ContactForm {


        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY) // This is crucial!
        private Long id;

        @NotBlank
        @Size(max = 80)
        private String name;

        @NotBlank @Email
        @Size(max = 120)
        private String email;

        @Size(max = 30)
        private String phone;

        @Size(max = 24)
        private String topic = "GENERAL";

        @NotBlank @Size(max = 120)
        private String subject;

        @NotBlank @Size(max = 5000)
        private String message;





        public ContactForm(Long id, String name, String email, String phone, String topic, String subject, String message) {
                this.id = id;
                this.name = name;
                this.email = email;
                this.phone = phone;
                this.topic = topic;
                this.subject = subject;
                this.message = message;
        }

        public ContactForm() {

        }

        public Long getId() {
                return id;
        }

        public void setId(Long id) {
                this.id = id;
        }

        // getters/setters
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

