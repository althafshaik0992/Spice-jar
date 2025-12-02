// src/main/java/com/example/foodapp/util/SessionCart.java
package com.example.foodapp.util;

import com.example.foodapp.model.Coupon;
import com.example.foodapp.model.OrderItem;
import com.example.foodapp.model.Product;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class SessionCart {

    public static final BigDecimal TAX_RATE = new BigDecimal("0.08");

    public boolean containsGiftCard() {
        if (items == null) return false;
        return items.stream()
                .anyMatch(i -> i.getType() == CartItem.Type.GIFT_CARD);
    }
// 8%

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionCart.class);
    /** Flip to true when you want verbose traces */
    private static final boolean DEBUG = true;
    private void log(String msg) {
        if (!DEBUG) return;
        try { LOGGER.info(msg); } catch (Throwable t) { System.out.println(msg); }
    }
     @Getter
     @Setter
    public static class Item {
        public Long productId;
        public String name;
        public BigDecimal unitPrice = BigDecimal.ZERO;
        public int qty = 1;
        public BigDecimal lineTotal() {
            return unitPrice.multiply(BigDecimal.valueOf(qty));
        }
        @Setter
        @Getter
        private CartItem.Type type;   // NEW

    }

    @Getter
    private final List<Item> items = new ArrayList<>();

    @Getter
    private String appliedCouponCode;
    private transient Coupon appliedCoupon;

    @Getter private BigDecimal subtotal   = BigDecimal.ZERO;
    @Getter private BigDecimal discount   = BigDecimal.ZERO;
    @Getter private BigDecimal tax        = BigDecimal.ZERO;
    @Getter private BigDecimal grandTotal = BigDecimal.ZERO;






    /** Total item count across lines */
    public int getCount() {
        return items.stream().mapToInt(i -> i.qty).sum();
    }

    /** Set/clear the coupon and mirror its code for the UI */
    public void setAppliedCoupon(Coupon coupon) {
        this.appliedCoupon = coupon;
        this.appliedCouponCode = (coupon == null ? null : coupon.getCode());
        log("[CART] coupon -> " + (coupon == null ? "null"
                : coupon.getCode() + " (" + coupon.getType() + " " + coupon.getValue() + ")"));
    }
    /** Actual Coupon object, used when saving CouponRedemption. */
    public Coupon getAppliedCoupon() {
        return appliedCoupon;
    }

    /* =========================
       Hydration helpers
       ========================= */

    /** Build cart from order items (uses real qty & unit price). */
    public void syncFromOrderItems(List<OrderItem> orderItems) {
        items.clear();
        log("[CART] syncFromOrderItems size=" + (orderItems == null ? 0 : orderItems.size()));
        if (orderItems != null) {
            for (OrderItem oi : orderItems) {
                Item it = new Item();
                it.productId = oi.getProductId();
                it.name      = oi.getProductName();

                int q = (oi.getQuantity() != null ? oi.getQuantity() : 1);
                if (q <= 0) q = 1;

                BigDecimal unit = BigDecimal.ZERO;
                if (oi.getUnitPrice() != null) {
                    unit = oi.getUnitPrice();
                } else if (oi.getPrice() != null) {
                    unit = oi.getPrice();
                } else if (oi.getLineTotal() != null) {
                    unit = oi.getLineTotal().divide(BigDecimal.valueOf(q), 2, RoundingMode.HALF_UP);
                }

                it.unitPrice = unit != null ? unit.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                it.qty       = q;

                items.add(it);
                log("  + OI -> id=" + it.productId + " name=" + it.name +
                        " unit=" + it.unitPrice + " qty=" + it.qty);
            }
        }
        recalc();
    }

    /**
     * Build the cart from a legacy list of Product (GlobalData.cart).
     * Defaults qty=1 (adjust if Product carries quantity).
     */
    public void syncFromProducts(List<Product> products) {
        items.clear();
        log("[CART] syncFromProducts size=" + (products == null ? 0 : products.size()));
        if (products != null) {
            for (Product p : products) {
                Item it = new Item();
                it.productId = p.getId();
                it.name      = p.getName();
                it.unitPrice = extractPrice(p).setScale(2, RoundingMode.HALF_UP);
                it.qty       = 1;
                items.add(it);
                log("  + PROD -> id=" + it.productId + " name=" + it.name +
                        " unit=" + it.unitPrice + " qty=" + it.qty);
            }
        }
        recalc();
    }

    /* =========================
       Mutators (optional)
       ========================= */

    public void addItem(Long productId, String name, BigDecimal unitPrice, int qty) {
        if (productId == null) return;
        var existing = items.stream()
                .filter(i -> Objects.equals(i.productId, productId))
                .findFirst()
                .orElse(null);

        BigDecimal incoming = safeBD(unitPrice);
        log("[CART] addItem id=" + productId + " name=" + name +
                " unit=" + incoming + " qty=" + qty +
                " (exists=" + (existing != null) + ")");

        if (existing == null) {
            Item it = new Item();
            it.productId = productId;
            it.name      = name;
            it.unitPrice = incoming.setScale(2, RoundingMode.HALF_UP);
            it.qty       = Math.max(1, qty);
            items.add(it);
            log("  -> NEW line: unit=" + it.unitPrice + " qty=" + it.qty);
        } else {
            // if existing price is 0, update it from incoming
            if (existing.unitPrice == null || existing.unitPrice.signum() == 0) {
                if (incoming.signum() > 0) {
                    existing.unitPrice = incoming.setScale(2, RoundingMode.HALF_UP);
                    log("  -> price updated to " + existing.unitPrice);
                }
            }
            existing.qty = Math.max(1, existing.qty + qty);
            log("  -> qty updated to " + existing.qty + " (unit=" + existing.unitPrice + ")");
        }
        recalc();
    }

    public void setQty(Long productId, int qty) {
        items.stream()
                .filter(i -> Objects.equals(i.productId, productId))
                .findFirst()
                .ifPresent(i -> {
                    log("[CART] setQty id=" + productId + " from " + i.qty + " to " + qty);
                    i.qty = Math.max(1, qty);
                    recalc();
                });
    }

    public void remove(Long productId) {
        log("[CART] remove id=" + productId);
        items.removeIf(i -> Objects.equals(i.productId, productId));
        recalc();
    }

    public void clear() {
        log("[CART] clear()");
        items.clear();
        setAppliedCoupon(null);
        recalc();
    }

    /* =========================
       Totals / Calculation
       ========================= */

    public void recalc() {
        log("[CART] recalc() start; items=" + items.size());
        for (Item it : items) {
            log("  = line id=" + it.productId + " name=" + it.name +
                    " unit=" + it.unitPrice + " qty=" + it.qty +
                    " line=" + it.lineTotal());
        }

        // Subtotal = sum(qty * unit)
        subtotal = items.stream()
                .map(Item::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        if (subtotal.signum() == 0 && !items.isEmpty()) {
            log("  ! Warning: items present but subtotal=0 (unitPrice missing?)");
        }

        BigDecimal d = BigDecimal.ZERO;

        if (appliedCoupon != null) {
            BigDecimal val = safeBD(appliedCoupon.getValue());
            switch (appliedCoupon.getType()) {
                case PERCENT -> d = subtotal.multiply(
                        val.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
                case  AMOUNT -> d = val;
            }
        }

        if (d.compareTo(subtotal) > 0) d = subtotal;    // cap at subtotal
        discount = d.setScale(2, RoundingMode.HALF_UP);

        BigDecimal taxableBase = subtotal.subtract(discount);
        if (taxableBase.signum() < 0) taxableBase = BigDecimal.ZERO;

        tax        = taxableBase.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        grandTotal = taxableBase.add(tax).setScale(2, RoundingMode.HALF_UP);

        log(String.format("  -> subtotal=%s discount=%s tax=%s grand=%s",
                subtotal, discount, tax, grandTotal));
    }

//    // inside SessionCart
//    public void syncFromCartItems(List<com.example.foodapp.util.CartItem> cartItems) {
//        items.clear();
//        if (cartItems != null) {
//            for (com.example.foodapp.util.CartItem c : cartItems) {
//                Item it = new Item();
//                it.productId = c.getProductId();
//                it.name      = c.getName();
//                // prefer BigDecimal getters if present; fall back to Number
//                BigDecimal up = c.getPrice() != null
//                        ? c.getPrice()
//                        : (c.getPrice() != null ? c.getPrice() : BigDecimal.ZERO);
//                it.unitPrice = up;
//                it.qty       = Math.max(1, c.getQty());
//                items.add(it);
//            }
//        }
//        recalc();
//    }



    // SessionCart.java
    public void syncFromCartItems(java.util.List<CartItem> cartItems) {
        items.clear();
        if (cartItems != null) {
            for (CartItem c : cartItems) {

                int qty = (c.getQty() <= 0) ? 1 : c.getQty();

                BigDecimal unit = c.getPrice();
                if (unit == null || unit.signum() < 0) unit = BigDecimal.ZERO;
                unit = unit.setScale(2, RoundingMode.HALF_UP);

                Item it = new Item();
                it.productId = c.getProductId();
                it.name      = (c.getName() == null ? "" : c.getName().trim());
                it.unitPrice = unit;
                it.qty       = qty;

                if (it.qty > 0) items.add(it);
            }
        }
        recalc();
    }




    /* =========================
       API convenience
       ========================= */

    public Map<String, Object> asTotalsMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("subtotal",   subtotal);
        m.put("discount",   discount);
        m.put("tax",        tax);
        m.put("grandTotal", grandTotal);
        m.put("cartCount",  getCount());
        m.put("applied",    appliedCouponCode);
        return m;
    }

    /* =========================
       Utils
       ========================= */

    private static BigDecimal safeBD(BigDecimal x) {
        return x == null ? BigDecimal.ZERO : x;
    }

    /** Tries several common getters/fields to extract a price from Product. */
    private BigDecimal extractPrice(Product p) {
        if (p == null) return BigDecimal.ZERO;

        try { var m = p.getClass().getMethod("getUnitPrice");
            var v = m.invoke(p); if (v instanceof BigDecimal bd) return bd;
            if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue()); } catch (Exception ignore) {}
        try { var m = p.getClass().getMethod("getPrice");
            var v = m.invoke(p); if (v instanceof BigDecimal bd) return bd;
            if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue()); } catch (Exception ignore) {}
        try { var m = p.getClass().getMethod("getSellingPrice");
            var v = m.invoke(p); if (v instanceof BigDecimal bd) return bd;
            if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue()); } catch (Exception ignore) {}
        try { var m = p.getClass().getMethod("getMrp");
            var v = m.invoke(p); if (v instanceof BigDecimal bd) return bd;
            if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue()); } catch (Exception ignore) {}

        try { var f = p.getClass().getDeclaredField("price");
            f.setAccessible(true);
            Object v = f.get(p);
            if (v instanceof BigDecimal bd) return bd;
            if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        } catch (Exception ignore) {}

        return BigDecimal.ZERO;
    }

    /** Quick manual dump if you want it in console without SLF4J formatting. */
    public void debugPrint() {
        System.out.println("========= SESSION CART DEBUG =========");
        if (items.isEmpty()) {
            System.out.println("Cart is empty!");
        } else {
            for (Item i : items) {
                System.out.println("Item: " + i.name +
                        " | id=" + i.productId +
                        " | qty=" + i.qty +
                        " | unitPrice=" + i.unitPrice +
                        " | lineTotal=" + i.lineTotal());
            }
        }
        System.out.println("Subtotal: " + subtotal);
        System.out.println("Discount: " + discount);
        System.out.println("Tax: " + tax);
        System.out.println("GrandTotal: " + grandTotal);
        System.out.println("=====================================");
    }
}
