// src/main/java/com/spicejar/account/PaymentMethodService.java
package com.example.foodapp.service;

import com.example.foodapp.model.PaymentMethod;
import com.example.foodapp.model.User;
import com.example.foodapp.repository.PaymentMethodRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class PaymentMethodService {
    private final PaymentMethodRepository repo;

    public PaymentMethodService(PaymentMethodRepository repo) { this.repo = repo; }

    public List<PaymentMethod> listForUser(User user) {
        return repo.findByUserIdOrderByDefaultMethodDescCreatedAtDesc(user.getId());
    }

    public PaymentMethod get(Long id) { return repo.findById(id).orElse(null); }

    @Transactional
    public PaymentMethod save(PaymentMethod p, User user) {
        p.setUser(user);
        // first card default
        if (listForUser(user).isEmpty()) p.setDefaultMethod(true);
        return repo.save(p);
    }

    @Transactional
    public void delete(PaymentMethod p) { repo.delete(p); }

    @Transactional
    public void makeDefault(PaymentMethod p, User user) {
        listForUser(user).forEach(x -> {
            x.setDefaultMethod(x.getId().equals(p.getId()));
            repo.save(x);
        });
    }
}
