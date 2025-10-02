package com.example.foodapp.service;

import com.example.foodapp.model.User;
import com.example.foodapp.repository.OrderRepository;
import com.example.foodapp.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Component
public class UserService {
    private final UserRepository repo;
    private  final OrderRepository orderRepo;
    private final PasswordEncoder encoder;
    private final EmailServiceWelcome emailService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;



    public UserService(UserRepository repo, OrderRepository orderRepo, PasswordEncoder encoder,  EmailServiceWelcome emailService) {
        this.repo = repo;
        this.orderRepo = orderRepo;
        this.encoder = encoder;
        this.emailService = emailService;
    }
    public Optional<User> findByUsernameOrEmail(String value) {
        return repo.findByUsernameIgnoreCaseOrEmailIgnoreCase(value, value);
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



    public Optional<User> findByEmail(String email){
        return repo.findByEmailIgnoreCase(email);
    }

    public void updatePassword(User user, String rawPassword){
        user.setPassword(encoder.encode(rawPassword));
        repo.save(user);
    }



    @Transactional
    public User createOAuthUser(String email, String firstName, String lastName, String displayName) {
        if (email == null || email.isBlank()) {
            // Defensive: Google should always give us an email with the "email" scope,
            // but bail safely if somehow it's missing.
            throw new IllegalArgumentException("OAuth provider did not return an email address");
        }

        // Either load an existing user or create a fresh one
        User u = repo.findByEmailIgnoreCase(email).orElseGet(User::new);

        // Core identity
        u.setEmail(email.trim().toLowerCase());
        u.setFirstName(firstName != null ? firstName : (displayName != null ? displayName : "User"));
        u.setLastName(lastName);
        u.setDisplayName(displayName != null ? displayName : u.getFirstName());
        u.setProvider("GOOGLE");

        // ►► IMPORTANT: ensure username is never null
        // Use email as username (common pattern). If you want something else,
        // derive it here, but ensure it's not null.
        if (u.getUsername() == null || u.getUsername().isBlank()) {
            u.setUsername(u.getEmail()); // or strip '@' part if you prefer
        }

        // Enable account & default role if new
        if (u.getRole() == null || u.getRole().isBlank()) {
            u.setRole("ROLE_USER");
        }
        u.setEnabled(true);

        // Set a random, unusable password placeholder (so BCrypt column isn’t null)
        if (u.getPassword() == null || u.getPassword().isBlank()) {
            u.setPassword(encoder.encode("oauth-" + System.nanoTime()));
        }

        // Bookkeeping
        var now = java.time.LocalDateTime.now();
        if (u.getCreatedAt() == null) {
            u.setCreatedAt(now);
        }
        u.setLastLoginAt(now);

        // Optional: keep other non-nullable fields safe if your schema marks them NOT NULL
        // (uncomment/adjust if your columns are NOT NULL)
        // if (u.getCountry() == null) u.setCountry("US");
        // if (u.getState() == null)   u.setState("");
        // if (u.getCity() == null)    u.setCity("");
        // if (u.getZip() == null)     u.setZip("");
        // if (u.getAddress() == null) u.setAddress("");

        return repo.save(u);
    }

    /** Update the last login timestamp for analytics/security. */
    @Transactional
    public void touchLastLogin(Long id) {
        repo.findById(id).ifPresent(u -> {
            u.setLastLoginAt(LocalDateTime.now());
            repo.save(u);
        });
    }

}
