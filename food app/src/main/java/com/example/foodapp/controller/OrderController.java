// src/main/java/com/example/foodapp/controller/OrderController.java
package com.example.foodapp.controller;

import com.example.foodapp.model.*;
import com.example.foodapp.util.Cart;
import com.example.foodapp.repository.CouponRepository;
import com.example.foodapp.repository.CouponRedemptionRepository;
import com.example.foodapp.service.*;
import com.example.foodapp.util.CartItem;
import com.example.foodapp.util.SessionCart;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/order")
public class OrderController extends BaseController {

    private final OrderService orderService;
    private final AddressService addressService;
    private final InventoryService inventoryService;
    private final EmailService emailService;
    private final SessionCart sessionCart;
    private final CouponRepository couponRepository;
    private final GiftCardService giftCardService;
    private final CouponRedemptionRepository couponRedemptionRepository;

    public OrderController(OrderService orderService,
                           AddressService addressService,
                           InventoryService inventoryService,
                           EmailService emailService,
                           SessionCart sessionCart,
                           CouponRepository couponRepository,
                           GiftCardService giftCardService,
                           CouponRedemptionRepository couponRedemptionRepository) {
        this.orderService = orderService;
        this.addressService = addressService;
        this.inventoryService = inventoryService;
        this.emailService = emailService;
        this.sessionCart = sessionCart;
        this.couponRepository = couponRepository;
        this.giftCardService = giftCardService;
        this.couponRedemptionRepository = couponRedemptionRepository;
    }

    @GetMapping("/checkout")
    public String checkout(@RequestParam(value = "orderId", required = false) Long orderId,
                           HttpSession session,
                           Model m) {

        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        boolean hydrated = false;

        if (orderId != null) {
            Order order = orderService.findById(orderId);
            if (order == null) {
                return "redirect:/orders";
            }
            sessionCart.syncFromOrderItems(order.getItems());
            hydrated = true;
        } else {
            Object obj = session.getAttribute("CART");
            if (obj instanceof Cart sc && sc.getItems() != null && !sc.getItems().isEmpty()) {
                sessionCart.syncFromCartItems(sc.getItems());
                hydrated = true;
            } else if (com.example.foodapp.util.GlobalData.cart != null
                    && !com.example.foodapp.util.GlobalData.cart.isEmpty()) {
                sessionCart.syncFromProducts(com.example.foodapp.util.GlobalData.cart);
                hydrated = true;
            }
        }

        if (!hydrated) {
            return "redirect:/cart/view";
        }

        sessionCart.recalc();

        boolean cartHasGiftCard = sessionCart.containsGiftCard();

        List<Coupon> availableCoupons = Collections.emptyList();
        if (!cartHasGiftCard) {
            List<Coupon> base;
            try {
                base = couponRepository.findActiveCurrentlyValid(LocalDate.now());
            } catch (Exception e) {
                base = couponRepository.findByActiveTrueOrderByStartsOnAscCodeAsc();
            }

            if (user != null) {
                Set<Long> redeemedIds = couponRedemptionRepository.findByUser(user)
                        .stream()
                        .map(cr -> cr.getCoupon().getId())
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

                availableCoupons = base.stream()
                        .filter(c -> c.getId() != null && !redeemedIds.contains(c.getId()))
                        .collect(Collectors.toList());
            } else {
                availableCoupons = base;
            }
        }

        m.addAttribute("currentUser", user);
        m.addAttribute("addresses", addressService.listForUser(user));
        m.addAttribute("cart", sessionCart);
        m.addAttribute("cartCount", sessionCart.getCount());
        m.addAttribute("availableCoupons", availableCoupons);
        m.addAttribute("cartHasGiftCard", cartHasGiftCard);

        return "checkout";
    }

    @PostMapping("/place")
    public String place(@RequestParam(required = false) Long addressId,
                        @RequestParam String customerName,
                        @RequestParam String email,
                        @RequestParam String phone,
                        @RequestParam String street,
                        @RequestParam String city,
                        @RequestParam String state,
                        @RequestParam String zip,
                        @RequestParam String country,
                        HttpSession session,
                        Model m) {

        User user = currentUser(session);
        if (user == null) return "redirect:/login";

        Cart cart = (Cart) session.getAttribute("CART");
        if (cart == null || cart.isEmpty()) return "redirect:/cart/view";

        // ensure SessionCart matches the latest cart items (for discount)
        if (sessionCart.getCount() <= 0
                && cart.getItems() != null
                && !cart.getItems().isEmpty()) {

            sessionCart.syncFromCartItems(cart.getItems());
            sessionCart.recalc();
        }

        BigDecimal discountFromSession = sessionCart.getDiscount() != null
                ? sessionCart.getDiscount()
                : BigDecimal.ZERO;

        BigDecimal discountFromCart = cart.getDiscount() != null
                ? cart.getDiscount()
                : BigDecimal.ZERO;

        BigDecimal effectiveDiscount =
                discountFromSession.compareTo(BigDecimal.ZERO) > 0
                        ? discountFromSession
                        : discountFromCart;

        if (addressId != null) {
            Address a = addressService.findById(addressId).orElse(null);
            if (a != null && a.getUser() != null && a.getUser().getId().equals(user.getId())) {
                customerName = nvl(a.getFullName(), customerName);
                phone = nvl(a.getPhone(), phone);
                String fullStreet = joinNonBlank(a.getLine1(), a.getLine2());
                street = nvl(fullStreet, street);
                street = nvl(a.getLine1(), street);
                city = nvl(a.getCity(), city);
                state = nvl(a.getState(), state);
                zip = nvl(a.getZip(), zip);
                country = nvl(a.getCountry(), country);
            }
        }

        Order o = new Order();
        o.setCreatedAt(LocalDateTime.now());
        o.setUserId(user.getId());
        o.setCustomerName(customerName);
        o.setEmail(email);
        o.setPhone(phone);
        o.setStreet(street);
        o.setCity(city);
        o.setState(state);
        o.setZip(zip);
        o.setCountry(country);
        o.setConfirmationNumber(orderService.generateUniqueConfirmationNumber());

        o.setItems(cart.getItems().stream().map(ci -> {
            OrderItem it = new OrderItem();
            it.setProductId(ci.getProductId());
            it.setProductName(ci.getName());
            it.setQuantity(ci.getQty());
            it.setPrice(ci.getPrice());
            it.setImageUrl(ci.getImageUrl());
            it.setLineTotal(
                    ci.getPrice()
                            .multiply(BigDecimal.valueOf(ci.getQty()))
                            .setScale(2, RoundingMode.HALF_UP)
            );
            return it;
        }).collect(Collectors.toList()));

        o.setDiscount(effectiveDiscount);
        o.setTotal(o.getGrandTotal());
        o.setStatus("PENDING_PAYMENT");

        // save order
        Order saved = orderService.save(o);

        // 🔹 record coupon redemption (one-time per user)
        if (user != null && sessionCart.getAppliedCoupon() != null
                && effectiveDiscount.compareTo(BigDecimal.ZERO) > 0) {

            CouponRedemption redemption = new CouponRedemption();
            redemption.setUser(user);
            redemption.setCoupon(sessionCart.getAppliedCoupon());
            redemption.setOrder(saved);
            redemption.setRedeemedAt(LocalDateTime.now()); // if field exists
            couponRedemptionRepository.save(redemption);

            // clear coupon from cart for next orders
            sessionCart.setAppliedCoupon(null);
            sessionCart.recalc();
        }

        // issue gift cards + emails
        for (CartItem ci : cart.getItems()) {
            if (ci.getProductId() != null && ci.getProductId() < 0) {
                for (int i = 0; i < ci.getQty(); i++) {
                    GiftCard gc = giftCardService.issuePurchasedCard(
                            ci.getPrice(), user.getId()
                    );
                    emailService.sendGiftCardEmail(email, customerName, gc);
                }
            }
        }

        cart.clear();
        emailService.sendOrderConfirmation(saved.getId());
        inventoryService.applyOrder(saved);
        emailService.sendOrderSurveyEmail(saved);
        m.addAttribute("cart", sessionCart);

        return "redirect:/payment/checkout?orderId=" + saved.getId();
    }

    private static String nvl(String v, String fb) {
        return (v == null || v.isBlank()) ? fb : v;
    }

    private static String joinNonBlank(String... parts) {
        return java.util.Arrays.stream(parts)
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining(" "));
    }
}
