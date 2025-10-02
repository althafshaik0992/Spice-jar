// src/main/java/com/example/foodapp/security/SessionUser.java
package com.example.foodapp.web;

import java.io.Serializable;

public record SessionUser(
        Long id,
        String firstName,
        String lastName,
        String email
) implements Serializable {}
