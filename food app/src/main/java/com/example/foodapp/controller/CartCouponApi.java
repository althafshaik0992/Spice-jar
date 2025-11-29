package com.example.foodapp.controller;

import com.example.foodapp.model.Product;
import com.example.foodapp.service.CouponService;
import com.example.foodapp.service.OrderService;
import com.example.foodapp.util.CartSession;
import com.example.foodapp.util.CouponApplyResponse;
import com.example.foodapp.util.SessionCart;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart/coupon")
public class CartCouponApi {

    private final CartSession cartSession;
    private final CouponService couponService;
    private final SessionCart sessionCart;
    private final OrderService orderService;
    // <-- keep name clear

    public CartCouponApi(CartSession cartSession, CouponService couponService, SessionCart sessionCart, OrderService orderService) {
        this.cartSession = cartSession;
        this.couponService = couponService;
        this.sessionCart = sessionCart;
        this.orderService = orderService;
    }

    /** Make sure SessionCart has real lines (qty + unit price) before math. */
    private void ensureCartHydrated(HttpServletRequest req) {
        // If already has lines, nothing to do
        if (!sessionCart.getItems().isEmpty()) {
            return;
        }

        // 1) If you keep orderId in session (or pass as request param), load from order
        Long orderId = null;
        Object o = req.getSession().getAttribute("CHECKOUT_ORDER_ID");
        if (o instanceof Long) orderId = (Long) o;
        if (orderId == null) {
            String p = req.getParameter("orderId");
            if (p != null && !p.isBlank()) {
                try { orderId = Long.valueOf(p); } catch (NumberFormatException ignore) {}
            }
        }

        if (orderId != null && orderService != null) {
            var order = orderService.findById(orderId);
            if (order != null && order.getItems() != null && !order.getItems().isEmpty()) {
                sessionCart.syncFromOrderItems(order.getItems());
                sessionCart.recalc();
                return;
            }
        }

        // 2) Fallback: hydrate from legacy GlobalData.cart (List<Product>)
        if (com.example.foodapp.util.GlobalData.cart != null &&
                !com.example.foodapp.util.GlobalData.cart.isEmpty()) {
            sessionCart.syncFromProducts(com.example.foodapp.util.GlobalData.cart);
            sessionCart.recalc();
            return;
        }

        // If we reach here, it’s truly empty
        sessionCart.recalc();
    }


    @PostMapping(
            path = "/apply",
            consumes = { MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.APPLICATION_JSON_VALUE },
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> apply(
            @RequestParam(value = "code", required = false) String formCode,
            @RequestBody(required = false) Map<String, Object> json,
            HttpServletRequest req
    ) {
        // Make sure sessionCart has the current items
        ensureCartHydrated(req);

        // 🔎 1) Block coupons when cart contains gift card items
        boolean cartHasGiftCard = sessionCart.getItems() != null &&
                sessionCart.getItems().stream().anyMatch(i ->
                        // you’re using negative productId for gift cards
                        (i.getProductId() != null && i.getProductId() < 0L) ||
                                // extra safety: name contains "gift card"
                                (i.getName() != null && i.getName().toLowerCase().contains("gift card"))
                );

        if (cartHasGiftCard) {
            // Clear any coupon just in case and recalc
            sessionCart.setAppliedCoupon(null);
            sessionCart.recalc();
            return ResponseEntity.ok(
                    payload("error", "Coupons can’t be applied to gift card purchases.")
            );
        }

        // 2) Normal coupon flow
        String code = (formCode != null && !formCode.isBlank()) ? formCode : null;
        if (code == null && json != null && json.get("code") != null) {
            code = String.valueOf(json.get("code"));
        }
        if (code == null || code.isBlank()) {
            return ResponseEntity.ok(payload("error", "Please enter a code."));
        }

        var v = couponService.validate(code.trim(), sessionCart.getSubtotal());
        if (v.error() != null) {
            sessionCart.setAppliedCoupon(null);
            sessionCart.recalc();
            return ResponseEntity.ok(payload("error", v.error()));
        }

        sessionCart.setAppliedCoupon(v.coupon());
        sessionCart.recalc();
        sessionCart.debugPrint(); // should show items & non-zero subtotal

        return ResponseEntity.ok(payload("success", "Applied " + v.coupon().getCode() + "."));
    }

    @PostMapping(
            path = "/remove",
            consumes = { MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.APPLICATION_JSON_VALUE },
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> remove(HttpServletRequest req) {
        ensureCartHydrated(req);
        sessionCart.setAppliedCoupon(null);
        sessionCart.recalc();
        return ResponseEntity.ok(payload("success", "Removed discount."));
    }


    // ---- helpers ----

    private Map<String,Object> payload(String status, String message){
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("status", status);
        m.put("message", message);
        m.put("subtotal",   sessionCart.getSubtotal());
        m.put("discount",   sessionCart.getDiscount());
        m.put("tax",        sessionCart.getTax());
        m.put("grandTotal", sessionCart.getGrandTotal());
        m.put("cartCount",  sessionCart.getCount());
        m.put("applied",    sessionCart.getAppliedCouponCode());
        return m;
    }


    // (kept in case something else in your app still calls it)
    private CouponApplyResponse toResp(String status, String msg){
        var c = cartSession.get();
        return new CouponApplyResponse(
                status, msg,
                c.getSubtotal(), c.getDiscount(), c.getTax(), c.getGrandTotal(),
                c.countItems()
        );
    }
}
