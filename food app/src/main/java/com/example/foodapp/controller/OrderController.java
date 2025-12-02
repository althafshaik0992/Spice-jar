// src/main/java/com/example/foodapp/controller/OrderController.java
package com.example.foodapp.controller;

import com.example.foodapp.model.*;
import com.example.foodapp.util.Cart;
import com.example.foodapp.repository.CouponRepository;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
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

    public OrderController(OrderService orderService, AddressService addressService, InventoryService inventoryService, EmailService emailService, SessionCart sessionCart, CouponRepository couponRepository, GiftCardService giftCardService) {
        this.orderService = orderService;
        this.addressService = addressService;
        this.inventoryService = inventoryService;
        this.emailService = emailService;
        this.sessionCart = sessionCart;
        this.couponRepository = couponRepository;
        this.giftCardService = giftCardService;
    }

//    @GetMapping("/checkout")
//    public String checkout(Model m, HttpSession session) {
//        User user = currentUser(session);
//        if (user == null) return "redirect:/login";
//
//        Cart cart = (Cart) session.getAttribute("CART");
//        if (cart == null || cart.isEmpty()) return "redirect:/cart/view";
//
//
//
//        m.addAttribute("currentUser", user);
//        m.addAttribute("addresses", addressService.listForUser(user));
//        m.addAttribute("cart", cart);
//        return "checkout";
//    }




//    @GetMapping("/checkout")
//    public String checkout(@RequestParam(required = false) Long orderId,
//                           Model m, HttpSession session) {
//        User user = currentUser(session);
//        if (user == null) return "redirect:/login";
//
//        // 1) Try the session cart first (this is what your cart page uses)
//        com.example.foodapp.util.Cart legacy = (com.example.foodapp.util.Cart) session.getAttribute("CART");
//        if (legacy != null && legacy.getItems() != null && !legacy.getItems().isEmpty()) {
//            sessionCart.syncFromCartItems(legacy.getItems());
//        }
//        // 2) If an orderId is provided, override using the order's items (real qty & unit price)
//        else if (orderId != null) {
//            var order = orderService.findById(orderId);
//            if (order == null) return "redirect:/orders";
//            sessionCart.syncFromOrderItems(order.getItems());
//        }
//        // 3) Fallback to legacy in-memory product list
//        else if (GlobalData.cart != null && !GlobalData.cart.isEmpty()) {
//            sessionCart.syncFromProducts(GlobalData.cart); // qty defaults to 1
//        } else {
//            // nothing to checkout
//            return "redirect:/cart/view";
//        }
//
//        sessionCart.recalc();
//
//        m.addAttribute("currentUser", user);
//        m.addAttribute("addresses", addressService.listForUser(user));
//        m.addAttribute("cart", sessionCart);              // IMPORTANT: pass SessionCart here
//        m.addAttribute("cartCount", sessionCart.getCount());
//        return "checkout";
//    }


    // OrderController.java

    @GetMapping("/checkout")
    public String checkout(@RequestParam(value = "orderId", required = false) Long orderId,
                           HttpSession session,
                           Model m) {

        // 1) Must be logged in
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        // 2) Hydrate SessionCart from the best available source
        boolean hydrated = false;

        if (orderId != null) {
            // From an existing order (has real qty & unitPrice)
            Order order = orderService.findById(orderId);
            if (order == null) {
                return "redirect:/orders";
            }
            sessionCart.syncFromOrderItems(order.getItems());
            hydrated = true;
        } else {
            // Try session "CART" (legacy Cart with List<CartItem>)
            Object obj = session.getAttribute("CART");
            if (obj instanceof Cart sc && sc.getItems() != null && !sc.getItems().isEmpty()) {
                sessionCart.syncFromCartItems(sc.getItems());
                hydrated = true;
            } else if (com.example.foodapp.util.GlobalData.cart != null
                    && !com.example.foodapp.util.GlobalData.cart.isEmpty()) {
                // Fallback: in-memory List<Product>
                sessionCart.syncFromProducts(com.example.foodapp.util.GlobalData.cart);
                hydrated = true;
            }
        }

        if (!hydrated) {
            // Nothing to checkout
            return "redirect:/cart/view";
        }

        // Recompute totals now that items exist
        sessionCart.recalc();

        // 3) Detect if cart contains any gift card line
        boolean cartHasGiftCard = sessionCart.containsGiftCard();

        // 4) Load active coupons ONLY if there is NO gift card in the cart
        List<Coupon> availableCoupons = Collections.emptyList();
        if (!cartHasGiftCard) {
            try {
                availableCoupons = couponRepository.findByActiveTrueOrderByStartsOnAscCodeAsc();
            } catch (Exception ignore) {
                availableCoupons = couponRepository.findAll().stream()
                        .filter(c -> Boolean.TRUE.equals(c.getActive()))
                        .sorted(
                                Comparator.comparing(
                                                (Coupon c) -> c.getStartsOn() == null
                                                        ? LocalDate.MIN
                                                        : c.getStartsOn()
                                        )
                                        .thenComparing(Coupon::getCode, String.CASE_INSENSITIVE_ORDER)
                        )
                        .toList();
            }
        }

        // 5) Model attrs for the view
        m.addAttribute("currentUser", user);
        m.addAttribute("addresses", addressService.listForUser(user));
        m.addAttribute("cart", sessionCart);                 // use SessionCart
        m.addAttribute("cartCount", sessionCart.getCount());
        m.addAttribute("availableCoupons", availableCoupons);
        m.addAttribute("cartHasGiftCard", cartHasGiftCard);  // for Thymeleaf / JS

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

        // --- hydrate SessionCart if it's currently empty (by count) ---
        if (sessionCart.getCount() <= 0
                && cart.getItems() != null
                && !cart.getItems().isEmpty()) {

            sessionCart.syncFromCartItems(cart.getItems());
            sessionCart.recalc();
        }

        // Prefer discount from SessionCart (what checkout used)
        BigDecimal discountFromSession = sessionCart.getDiscount() != null
                ? sessionCart.getDiscount()
                : BigDecimal.ZERO;

        // Fallback to legacy Cart discount if needed
        BigDecimal discountFromCart = cart.getDiscount() != null
                ? cart.getDiscount()
                : BigDecimal.ZERO;

        BigDecimal effectiveDiscount =
                discountFromSession.compareTo(BigDecimal.ZERO) > 0
                        ? discountFromSession
                        : discountFromCart;
        // -------------------------------------------------------------

        // override with saved address (defensive check)
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

        // 👉 discount now comes from SessionCart (checkout) whenever possible
        o.setDiscount(effectiveDiscount);

        // uses Order#getGrandTotal(), which already subtracts discount + adds tax
        o.setTotal(o.getGrandTotal());

        o.setStatus("PENDING_PAYMENT");

        orderService.save(o);

        // Issue gift cards + emails
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
        emailService.sendOrderConfirmation(o.getId());
        inventoryService.applyOrder(o);
        emailService.sendOrderSurveyEmail(o);
        m.addAttribute("cart", sessionCart);


        return "redirect:/payment/checkout?orderId=" + o.getId();
    }


    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
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
