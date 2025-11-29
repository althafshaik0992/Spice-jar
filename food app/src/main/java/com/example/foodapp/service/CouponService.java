package com.example.foodapp.service;

import com.example.foodapp.model.Coupon;
import com.example.foodapp.repository.CouponRepository;
import com.example.foodapp.util.SessionCart;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class CouponService {

    private final CouponRepository repo;

    public CouponService(CouponRepository repo) {
        this.repo = repo;
    }

    /** Apply a code directly to the SessionCart (validates first). */
    public Result applyToSessionCart(String rawCode, SessionCart cart) {
        if (rawCode == null || rawCode.isBlank()) {
            return Result.error("Please enter a code.");
        }
        var v = validate(rawCode.trim(), cart.getSubtotal());
        if (v.error() != null) {
            cart.setAppliedCoupon(null);
            cart.recalc();
            return Result.error(v.error());
        }
        cart.setAppliedCoupon(v.coupon());
        cart.recalc();
        return Result.ok("Applied " + v.coupon().getCode() + ".");
    }

    /** Remove any applied coupon from the SessionCart. */
    public Result removeFromSessionCart(SessionCart cart) {
        cart.setAppliedCoupon(null);
        cart.recalc();
        return Result.ok("Removed discount.");
    }

    /** Used by API/controller to verify a code against a given subtotal. */
    public record Validation(Coupon coupon, String error) {}

    public Validation validate(String code, BigDecimal subtotal) {
        if (code == null || code.isBlank()) {
            return new Validation(null, "Enter a code.");
        }

        var c = repo.findByCodeIgnoreCase(code.trim().toUpperCase()).orElse(null);
        if (c == null) return new Validation(null, "Code not found.");
        if (!Boolean.TRUE.equals(c.getActive())) return new Validation(null, "Code is inactive.");

        var today = LocalDate.now();
        if (c.getStartsOn() != null && today.isBefore(c.getStartsOn()))
            return new Validation(null, "Code is not active yet.");
        if (c.getExpiresOn() != null && today.isAfter(c.getExpiresOn()))
            return new Validation(null, "Code has expired.");

        if (c.getMinSubtotal() != null && subtotal != null
                && subtotal.compareTo(c.getMinSubtotal()) < 0) {
            return new Validation(null, "Minimum subtotal $" + c.getMinSubtotal() + " required.");
        }

        // Sanity checks for type/value
        if (c.getType() == null || c.getValue() == null)
            return new Validation(null, "Coupon is misconfigured.");

        switch (c.getType()) {
            case PERCENT -> {
                if (c.getValue().compareTo(BigDecimal.ZERO) <= 0)
                    return new Validation(null, "Coupon percent must be > 0.");
            }
            case AMOUNT -> {
                if (c.getValue().compareTo(BigDecimal.ZERO) <= 0)
                    return new Validation(null, "Coupon amount must be > 0.");
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
