package com.example.foodapp.service;

import com.example.foodapp.model.User;
import com.example.foodapp.repository.OrderRepository;
import com.example.foodapp.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

@Service
public class UserService {
    private final UserRepository repo;
    private  final OrderRepository orderRepo;
    private final PasswordEncoder encoder;

    public UserService(UserRepository repo, OrderRepository orderRepo, PasswordEncoder encoder) {
        this.repo = repo;
        this.orderRepo = orderRepo;
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

    @Transactional
    public boolean deleteUser(Long userId) {
        // First, check if the user exists.
        Optional<User> user = repo.findById(userId);
        if (user.isPresent()) {
            // Delete all orders associated with this user.
            orderRepo.deleteAllByUserId(userId);
            // Delete the user themselves.
            repo.deleteById(userId);
            return true;
        }
        return false;
    }

    public User getCurrentUser(HttpSession session) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return null; // no logged in user
        }

        String username = auth.getName();
        return repo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}
