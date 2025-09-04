package com.example.foodapp.service;

import com.example.foodapp.model.User;
import com.example.foodapp.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {
    private final UserRepository repo;
    private final PasswordEncoder encoder;

    public UserService(UserRepository repo, PasswordEncoder encoder) {
        this.repo = repo;
        this.encoder = encoder;
    }

    // Basic registration with username/password
    public User register(String username, String rawPassword) {
        User u = new User(username, encoder.encode(rawPassword), "ROLE_USER");
        return repo.save(u);
    }

    // Extended registration with extra fields
    public User register(String firstName,
                         String lastName,
                         String address,
                         String email,
                         String phone,
                         String username,
                         String rawPassword) {
        User u = new User(firstName, lastName, address, email, phone,
                username, encoder.encode(rawPassword), "ROLE_USER");
        return repo.save(u);
    }

    // Admin registration
    public User registerAdmin(String username, String rawPassword) {
        User u = new User(username, encoder.encode(rawPassword), "ROLE_ADMIN");
        return repo.save(u);
    }

    public Optional<User> findByUsername(String u) {
        return repo.findByUsername(u);
    }

    public boolean checkPassword(String raw, String encoded) {
        return encoder.matches(raw, encoded);
    }

    public Optional<User> findById(Long id) { return repo.findById(id); }
    public User save(User u) { return repo.save(u); }
}
