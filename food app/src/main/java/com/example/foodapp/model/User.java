package com.example.foodapp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "app_user")
@Getter
@Setter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstName;
    private String lastName;
    private String address;

    private String state;
    private String City;
    private String Country;
    private String zip;

    @Column(unique = true)
    private String email;

    private String provider;        // "google" for OAuth
    private Boolean enabled = true;

    private String displayName;

    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime lastLoginAt;

    private String phone;
    // com.example.foodapp.model.User
    @Column(name = "avatar_url")
    private String avatarUrl;







    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;


    private String role = "USER";

    public User() {
    }

    public User(String username, String password, String role) {

        this.username = username;
        this.password = password;
        this.role = role;
    }


    public User( String firstName, String lastName, String address, String email, String phone, String username, String password, String role) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.address = address;
        this.email = email;
        this.phone = phone;
        this.username = username;
        this.password = password;
        this.role = role;
    }



    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }



    public String getUsername() {
        return username;
    }



    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
    public String getFirstName() {
        return this.firstName;
    }
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return this.lastName;
    }
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getAddress() {
        return this.address;
    }
    public void setAddress(String address) {
        this.address = address;
    }

    public String getEmail() {
        return this.email;
    }
    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return this.phone;
    }
    public void setPhone(String phone) {
        this.phone = phone;
    }


    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

}
