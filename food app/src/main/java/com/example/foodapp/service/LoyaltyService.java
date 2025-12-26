package com.example.foodapp.service;

import com.example.foodapp.model.LoyaltyLedger;
import com.example.foodapp.model.LoyaltyWallet;
import com.example.foodapp.repository.LoyaltyLedgerRepository;
import com.example.foodapp.repository.LoyaltyWalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@Transactional
public class LoyaltyService {

    // $10 spent => 1 point
    // LoyaltyService.java

    private static final BigDecimal EARN_RATE_DOLLARS = new BigDecimal("10");

    // ✅ 50 points = $1  =>  $ per point = 1/50 = 0.02
    private static final BigDecimal POINTS_PER_DOLLAR = new BigDecimal("50");
    private static final BigDecimal DOLLARS_PER_POINT =
            BigDecimal.ONE.divide(POINTS_PER_DOLLAR, 4, RoundingMode.HALF_UP); // 0.0200



    private final LoyaltyWalletRepository walletRepo;
    private final LoyaltyLedgerRepository ledgerRepo;

    public LoyaltyService(LoyaltyWalletRepository walletRepo,
                          LoyaltyLedgerRepository ledgerRepo) {
        this.walletRepo = walletRepo;
        this.ledgerRepo = ledgerRepo;
    }

    // ---------------------------
    // Helpers
    // ---------------------------
    private LoyaltyWallet getOrCreateWallet(Long userId) {
        return walletRepo.findByUserId(userId).orElseGet(() -> {
            LoyaltyWallet w = new LoyaltyWallet();
            w.setUserId(userId);           // ✅ critical
            w.setPointsBalance(0);
            return walletRepo.save(w);
        });
    }

    // $10 spent => 1 point (floor)
    public int calculateEarnPoints(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return 0;

        return amount
                .divide(EARN_RATE_DOLLARS, 0, RoundingMode.FLOOR)
                .intValue();
    }

    // ---------------------------
    // Read
    // ---------------------------
    @Transactional(readOnly = true)
    public int getBalance(Long userId) {
        if (userId == null) return 0;
        return walletRepo.findByUserId(userId)
                .map(LoyaltyWallet::getPointsBalance)
                .orElse(0);
    }

    // ---------------------------
    // Earn
    // ---------------------------
    public void earn(Long userId, Long orderId, BigDecimal paidAmount) {
        if (userId == null || paidAmount == null) return;

        int points = calculateEarnPoints(paidAmount);
        if (points <= 0) return;

        LoyaltyWallet wallet = getOrCreateWallet(userId);
        wallet.setPointsBalance(wallet.getPointsBalance() + points);
        walletRepo.save(wallet);

        LoyaltyLedger tx = new LoyaltyLedger();
        tx.setUserId(userId);
        tx.setOrderId(orderId);
        tx.setType(LoyaltyLedger.Type.EARN);          // ✅ fix
        tx.setPoints(points);
        tx.setNote("Earned points for paid order");
        tx.setCreatedAt(LocalDateTime.now());         // ✅ if your entity has createdAt
        ledgerRepo.save(tx);
    }

    // ---------------------------
    // Redeem
    // ---------------------------
    /**
     * Redeem points and return $ discount amount.
     */
    @Transactional
    public BigDecimal redeem(Long userId, Long orderId, int pointsUsed) {
        if (userId == null || pointsUsed <= 0) {
            throw new IllegalArgumentException("Invalid points");
        }

        // ✅ enforce minimum + multiples of 50
        if (pointsUsed < 50 || (pointsUsed % 50 != 0)) {
            throw new IllegalArgumentException("Points must be at least 50 and in multiples of 50.");
        }

        LoyaltyWallet wallet = getOrCreateWallet(userId);

        if (wallet.getPointsBalance() < pointsUsed) {
            throw new IllegalStateException("Not enough wallet points");
        }

        wallet.setPointsBalance(wallet.getPointsBalance() - pointsUsed);
        walletRepo.save(wallet);

        LoyaltyLedger tx = new LoyaltyLedger();
        tx.setUserId(userId);
        tx.setOrderId(orderId);
        tx.setType(LoyaltyLedger.Type.REDEEM);
        tx.setPoints(-pointsUsed);
        tx.setNote("Redeemed points at checkout");
        ledgerRepo.save(tx);

        // ✅ discount = pointsUsed * (1/50)
        return new BigDecimal(pointsUsed)
                .multiply(DOLLARS_PER_POINT)
                .setScale(2, RoundingMode.HALF_UP);
    }

}
