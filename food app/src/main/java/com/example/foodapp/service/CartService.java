package com.example.foodapp.service;

import com.example.foodapp.repository.CartItemRepository;
import com.example.foodapp.repository.CartRepository;
import com.example.foodapp.util.Cart;
import com.example.foodapp.util.CartItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class CartService {

    private final CartRepository cartRepo;
    private final CartItemRepository itemRepo;
    private final ObjectMapper objectMapper;

    public CartService(CartRepository cartRepo, CartItemRepository itemRepo, ObjectMapper objectMapper) {
        this.cartRepo = cartRepo;
        this.itemRepo = itemRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Cart getOrCreateActiveCart(Long userId) {
        return cartRepo.findActiveCartByUserId(userId)
                .orElseGet(() -> {
                    Cart c = new Cart();
                    c.setUserId(userId);
                    c.setStatus(Cart.Status.OPEN);
                    return cartRepo.save(c);
                });
    }



    @Transactional
    public Cart getOrCreateCartForUser(Long userId) {
        Optional<Cart> existing = cartRepo.findByUserIdAndStatus(userId, Cart.Status.OPEN);
        if (existing.isPresent()) {
            return existing.get();
        }
        Cart c = new Cart();
        c.setUserId(userId);
        c.setStatus(Cart.Status.OPEN);
        return cartRepo.save(c);
    }

    @Transactional
    public void addGiftCardToCart(Long userId,
                                  BigDecimal amount,
                                  String delivery,          // "EMAIL" | "PHYSICAL"
                                  String recipientName,
                                  String recipientEmail,
                                  LocalDate scheduleDate,
                                  Long shipToAddressId,
                                  String fromName,
                                  String message) {

        Cart cart = getOrCreateCartForUser(userId);

        // Display
        String displayName = "SpiceJar Gift Card";
        String desc = "Digital gift card for " +
                (recipientName != null && !recipientName.isBlank() ? recipientName : "your friend");

        // sku based on delivery
        String sku = "GC-" + (delivery == null ? "EMAIL" : delivery.toUpperCase(Locale.ROOT));

        // simple JSON metadata
        String metaJson = String.format(
                "{\"type\":\"GIFT_CARD\",\"delivery\":\"%s\",\"recipientName\":\"%s\",\"recipientEmail\":\"%s\",\"scheduleDate\":\"%s\",\"shipToAddressId\":%s,\"fromName\":\"%s\",\"message\":\"%s\"}",
                delivery,
                recipientName == null ? "" : recipientName,
                recipientEmail == null ? "" : recipientEmail,
                scheduleDate == null ? "" : scheduleDate.toString(),
                shipToAddressId == null ? "null" : shipToAddressId.toString(),
                fromName == null ? "" : fromName,
                message == null ? "" : message
        );

        CartItem item = new CartItem(
                null,               // productId -> null for pure gift card
                displayName,
                1,                  // qty
                amount,
                "/images/giftcard-preview.png", // make sure this exists
                desc,
                null                // weightGrams
        );

        // 🔴 CRITICAL: wire up relational fields
        item.setCart(cart);                        // this fixes cart_id null
        item.setType(CartItem.Type.GIFT_CARD);
        item.setSku(sku);
        item.setMetaJson(metaJson);
        item.setSavedForLater(false);

        cart.getItems().add(item);

        cartRepo.save(cart); // cascade persists the CartItem with cart_id set
    }
}

