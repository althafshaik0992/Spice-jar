// src/main/java/com/example/foodapp/Ai/InMemoryCartService.java
package com.example.foodapp.Ai;

import com.example.foodapp.model.Product;
import com.example.foodapp.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Primary
@RequiredArgsConstructor
public class InMemoryCartService implements CartService {

    private final ProductRepository productRepository;

    // sessionId -> (productId -> qty)
    private final Map<String, Map<Long, Integer>> carts = new ConcurrentHashMap<>();

    @Override
    public void add(String sessionId, Long productId, int qty) {
        if (sessionId == null || productId == null || qty <= 0) return;
        carts.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                .merge(productId, qty, Integer::sum);
    }

    @Override
    public List<CartLine> items(String sessionId) {
        Map<Long, Integer> lines = carts.getOrDefault(sessionId, Map.of());
        List<CartLine> out = new ArrayList<>();
        for (var e : lines.entrySet()) {
            Long pid = e.getKey();
            int q = e.getValue();
            Product p = productRepository.findById(pid).orElse(null);
            if (p == null) continue;
            BigDecimal price = p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO;
            BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(q));
            out.add(new CartLine(pid, p.getName(), q, lineTotal));
        }
        return out;
    }

    @Override
    public BigDecimal total(String sessionId) {
        return items(sessionId).stream()
                .map(CartLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public Long freezeToOrder(String sessionId) {
        // For local/dev: just clear the cart and return null.
        // If you want to create a real Order here, inject your OrderService and build it.
        carts.remove(sessionId);
        return null;
    }
}
