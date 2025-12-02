package com.example.foodapp.service;

import com.example.foodapp.model.Coupon;
import com.example.foodapp.model.User;
import com.example.foodapp.repository.CouponRedemptionRepository;
import com.example.foodapp.repository.CouponRepository;
import com.example.foodapp.util.SessionCart;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class CouponService {

    private final CouponRepository repo;
    private final CouponRedemptionRepository redemptionRepo;

    public CouponService(CouponRepository repo,
                         CouponRedemptionRepository redemptionRepo) {
        this.repo = repo;
        this.redemptionRepo = redemptionRepo;
    }

    /** Apply a code directly to the SessionCart (validates first) when we know the user. */
    public Result applyToSessionCart(String rawCode, User user, SessionCart cart) {
        if (rawCode == null || rawCode.isBlank()) {
            return Result.error("Please enter a code.");
        }
        var v = validate(rawCode.trim(), user, cart.getSubtotal());
        if (v.error() != null) {
            cart.setAppliedCoupon(null);
            cart.recalc();
            return Result.error(v.error());
        }
        cart.setAppliedCoupon(v.coupon());
        cart.recalc();
        return Result.ok("Applied " + v.coupon().getCode() + ".");
    }

    /** Backwards-compatible overload (no user = no “once per user” check). */
    public Result applyToSessionCart(String rawCode, SessionCart cart) {
        return applyToSessionCart(rawCode, null, cart);
    }

    /** Remove any applied coupon from the SessionCart. */
    public Result removeFromSessionCart(SessionCart cart) {
        cart.setAppliedCoupon(null);
        cart.recalc();
        return Result.ok("Removed discount.");
    }

    // ---------- validation ----------

    public record Validation(Coupon coupon, String error) {}

    public Validation validate(String code, User user, BigDecimal subtotal) {

        String trimmed = (code == null) ? "" : code.trim();
        if (trimmed.isBlank()) {
            return new Validation(null, "Enter a code.");
        }

        Coupon c = repo.findByCodeIgnoreCase(trimmed).orElse(null);
        if (c == null) {
            return new Validation(null, "Code not found.");
        }

        // 🔒 Only once per user
        if (user != null && redemptionRepo != null &&
                redemptionRepo.existsByUserAndCoupon(user, c)) {
            return new Validation(null, "You have already used this coupon.");
        }

        if (!Boolean.TRUE.equals(c.getActive())) {
            return new Validation(null, "Code is inactive.");
        }

        var today = LocalDate.now();
        if (c.getStartsOn() != null && today.isBefore(c.getStartsOn())) {
            return new Validation(null, "Code is not active yet.");
        }
        if (c.getExpiresOn() != null && today.isAfter(c.getExpiresOn())) {
            return new Validation(null, "Code has expired.");
        }

        if (c.getMinSubtotal() != null && subtotal != null
                && subtotal.compareTo(c.getMinSubtotal()) < 0) {
            return new Validation(null,
                    "Minimum subtotal $" + c.getMinSubtotal() + " required.");
        }

        // Sanity checks for type/value
        if (c.getType() == null || c.getValue() == null) {
            return new Validation(null, "Coupon is misconfigured.");
        }

        switch (c.getType()) {
            case PERCENT -> {
                if (c.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                    return new Validation(null, "Coupon percent must be > 0.");
                }
            }
            case AMOUNT -> {
                if (c.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                    return new Validation(null, "Coupon amount must be > 0.");
                }
            }
        }

        return new Validation(c, null);
    }

    /** Simple result wrapper. */
    public record Result(boolean ok, String message) {
        public static Result ok(String m)    { return new Result(true, m); }
        public static Result error(String m) { return new Result(false, m); }
    }
}
