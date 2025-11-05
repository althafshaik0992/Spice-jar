// src/main/java/com/example/foodapp/service/PasswordResetService.java
package com.example.foodapp.service;

import com.example.foodapp.model.PasswordResetToken;
import com.example.foodapp.model.User;
import com.example.foodapp.repository.PasswordResetTokenRepository;
import com.example.foodapp.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PasswordResetService {

    public record ResetResult(boolean ok, boolean tokenValid, String message) {}

    private final PasswordResetTokenRepository tokens;
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public PasswordResetService(PasswordResetTokenRepository tokens,
                                UserRepository users,
                                PasswordEncoder encoder) {
        this.tokens = tokens; this.users = users; this.encoder = encoder;
    }

    public PasswordResetToken createToken(User user){
        PasswordResetToken t = new PasswordResetToken();
        t.setToken(UUID.randomUUID().toString().replace("-", ""));
        t.setUser(user);
        t.setExpiresAt(LocalDateTime.now().plusHours(2));
        t.setUsed(false);
        return tokens.save(t);
    }

    public boolean validate(String token){
        return tokens.findByToken(token)
                .filter(t -> !t.isUsed())
                .filter(t -> t.getExpiresAt().isAfter(LocalDateTime.now()))
                .isPresent();
    }

    public ResetResult resetPassword(String token, String newPassword){
        var opt = tokens.findByToken(token);
        if (opt.isEmpty()) return new ResetResult(false, false, "Invalid token");

        var t = opt.get();
        if (t.isUsed() || t.getExpiresAt().isBefore(LocalDateTime.now()))
            return new ResetResult(false, false, "Token expired");

        var user = t.getUser();
        user.setPassword(encoder.encode(newPassword));
        users.save(user);

        t.setUsed(true);
        tokens.save(t);

        return new ResetResult(true, true, "Password updated");
    }
}
