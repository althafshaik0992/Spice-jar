// src/main/java/com/example/foodapp/service/GiftCardService.java
package com.example.foodapp.service;

import com.example.foodapp.model.GiftCard;
import com.example.foodapp.model.GiftCardRedemption;
import com.example.foodapp.repository.GiftCardRedemptionRepository;
import com.example.foodapp.repository.GiftCardRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class GiftCardService {

    private final GiftCardRepository repo;
    private final SecureRandom random = new SecureRandom();
    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final GiftCardRedemptionRepository redRepo;

    public GiftCardService(GiftCardRepository repo, GiftCardRedemptionRepository redRepo) {
        this.repo = repo;
        this.redRepo = redRepo;
    }
    public List<GiftCard> findAllForUser(Long userId) {
        if (userId == null) return List.of();

        List<GiftCard> cards = repo
                .findByAssignedUserIdOrPurchasedByUserIdOrderByCreatedAtDesc(userId, userId);

        // (optional) small debug log so you can see what’s happening
        System.out.println("=== GIFT CARDS FOR USER " + userId + " ===");
        System.out.println("count = " + cards.size());
        for (GiftCard gc : cards) {
            System.out.printf("  id=%d code=%s balance=%s assigned=%s purchased=%s%n",
                    gc.getId(),
                    gc.getCode(),
                    gc.getBalance(),
                    gc.getAssignedUserId(),
                    gc.getPurchasedByUserId());
        }
        System.out.println("=========================================");

        return cards;
    }







    public BigDecimal totalBalance(Long userId) {
        if (userId == null) return BigDecimal.ZERO;

        return repo.findByAssignedUserId(userId).stream()
                .filter(GiftCard::isActive)
                .filter(gc -> !gc.isExpired())
                .map(GiftCard::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Redeem a code into the user's account.
     * If card is unclaimed, we attach it to this user.
     * If already claimed by same user, we allow (idempotent).
     * If claimed by someone else, we throw.
     */
    @Transactional
    public void redeemToUser(Long userId, String rawCode) {
        String code = rawCode.trim();

        GiftCard gc = repo.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new IllegalArgumentException("Gift card code not found."));

        if (!gc.isActive()) throw new IllegalArgumentException("This gift card is inactive.");
        if (gc.isExpired()) throw new IllegalArgumentException("This gift card has expired.");

        Long owner = gc.getAssignedUserId();
        if (owner == null) {
            // first user who redeems becomes owner
            gc.setAssignedUserId(userId);
            repo.save(gc);
            return;
        }

        if (!owner.equals(userId)) {
            throw new IllegalArgumentException("This gift card is already attached to another account.");
        }

        // already owned by this user → OK, idempotent
    }

    @Transactional
    public void removeFromUser(Long userId, Long giftCardId) {
        if (userId == null) return;

        GiftCard gc = repo.findByIdAndAssignedUserId(giftCardId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Gift card not found for your account."));

        gc.setAssignedUserId(null); // detach
        repo.save(gc);
    }


    /**
     * Apply oldest cards first; returns amount covered by gift cards.
     */
    @Transactional
    public BigDecimal applyTowardsTotal(Long userId, BigDecimal orderTotal) {
        var cards = repo.findByAssignedUserId(userId).stream()
                .filter(gc -> gc.isActive() && !gc.isExpired()
                        && gc.getBalance() != null && gc.getBalance().compareTo(BigDecimal.ZERO) > 0)
                // if you do NOT have createdAt, replace with Comparator.comparing(GiftCard::getId)
                .sorted(Comparator.comparing(GiftCard::getCreatedAt))
                .toList();

        BigDecimal remaining = orderTotal;
        for (var gc : cards) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal use = gc.getBalance().min(remaining);
            gc.setBalance(gc.getBalance().subtract(use));
            repo.save(gc);
            remaining = remaining.subtract(use);
        }
        return orderTotal.subtract(remaining); // amount covered by gift cards
    }

    // === ADMIN OPERATIONS ===

    /**
     * Toggle active/inactive
     */
    @Transactional
    public void toggleActive(Long id) {
        var gc = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Gift card not found."));
        gc.setActive(!gc.isActive());
        repo.save(gc);
    }

    /**
     * Delete permanently (switch to soft delete if needed)
     */
    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) return;
        repo.deleteById(id);
    }

    public List<GiftCard> findAll() {
        return repo.findAll();
    }

    private String randomBlock(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * Generate a candidate code like SJ-ABCD-1234.
     */
    private String generateCandidateCode() {
        return "SJ-" + randomBlock(4) + "-" + randomBlock(4);
    }

    /**
     * Generate a code that is unique in DB (case-insensitive).
     */
    private String generateUniqueCode() {
        String code;
        do {
            code = generateCandidateCode();
        } while (repo.findByCodeIgnoreCase(code).isPresent());
        return code;
    }

    /** Admin create */
    /**
     * Admin create – code optional, auto-generated if blank.
     */
    @Transactional
    public GiftCard create(String code, BigDecimal amount, OffsetDateTime expiresAt) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }

        final String normalized;

        // If no code supplied, generate one
        if (code == null || code.isBlank()) {
            normalized = generateUniqueCode();
        } else {
            normalized = code.trim().toUpperCase(Locale.ROOT);

            // ensure unique (case-insensitive) only when manually provided
            if (repo.findByCodeIgnoreCase(normalized).isPresent()) {
                throw new IllegalArgumentException("A gift card with this code already exists.");
            }
        }

        GiftCard gc = new GiftCard();
        gc.setCode(normalized);
        gc.setOriginalAmount(amount);
        gc.setBalance(amount);
        gc.setActive(true);
        gc.setAssignedUserId(null); // make sure it's not pre-owned

        gc.setExpiresAt(expiresAt == null ? null : expiresAt.toLocalDate());

        return repo.save(gc);
    }


    // Issue a gift card that was purchased from checkout.
// Code is auto-generated, no expiry by default.
    @Transactional
    public GiftCard issuePurchasedCard(BigDecimal amount,Long ownerUserId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Gift card amount must be greater than zero.");
        }

        GiftCard gc = create(null, amount, null);

        gc.setAssignedUserId(ownerUserId);
        return repo.save(gc);

    }

    public BigDecimal usableBalanceForUser(Long userId) {
        if (userId == null) return BigDecimal.ZERO;

        List<GiftCard> cards = repo.findByAssignedUserId(userId);
        if (cards == null || cards.isEmpty()) return BigDecimal.ZERO;

        return cards.stream()
                .filter(GiftCard::isActive)
                .filter(gc -> !gc.isExpired())
                .map(GiftCard::getBalance)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public BigDecimal consumeBalanceForUser(Long userId,
                                            BigDecimal requested,
                                            String reason,
                                            Long orderId) {
        if (userId == null || requested == null || requested.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal remaining = requested.setScale(2, RoundingMode.HALF_UP);

        List<GiftCard> cards = repo.findByAssignedUserId(userId);
        if (cards == null || cards.isEmpty()) return BigDecimal.ZERO;

        // Oldest expiry first
        cards.sort(Comparator
                .comparing(GiftCard::getExpiresAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(GiftCard::getId));

        for (GiftCard gc : cards) {
            if (!gc.isActive() || gc.isExpired()) continue;

            BigDecimal bal = gc.getBalance();
            if (bal == null || bal.signum() <= 0) continue;

            BigDecimal use = bal.min(remaining);
            if (use.signum() <= 0) continue;

            gc.setBalance(bal.subtract(use));
            if (gc.getBalance().signum() == 0) {
                gc.setActive(false);
            }
            repo.save(gc);

            // ✅ use redRepo here, not repo
            GiftCardRedemption red = new GiftCardRedemption();
            red.setGiftCard(gc);
            red.setAmount(use);
            red.setReason(reason);   // now exists
            red.setOrderId(orderId); // now exists
            redRepo.save(red);

            remaining = remaining.subtract(use);
            if (remaining.signum() <= 0) break;
        }

        return requested.subtract(remaining).setScale(2, RoundingMode.HALF_UP);
    }

// GiftCardService.java

//    @Transactional
//    public GiftCard issuePurchasedCard(BigDecimal amount, Long buyerUserId) {
//        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
//            throw new IllegalArgumentException("Gift card amount must be greater than zero.");
//        }
//
//        GiftCard gc = new GiftCard();
//        gc.setCode(generateUniqueCode());
//        gc.setOriginalAmount(amount.setScale(2, RoundingMode.HALF_UP));
//        gc.setBalance(amount.setScale(2, RoundingMode.HALF_UP));
//        gc.setActive(true);
//        gc.setAssignedUserId(null);          // not attached yet – needs redeem
//        gc.setPurchasedByUserId(buyerUserId);
//        gc.setCreatedAt(LocalDateTime.now());
//
//        return repo.save(gc);
//    }






}

