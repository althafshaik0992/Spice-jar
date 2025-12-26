// src/main/java/com/example/foodapp/controller/OrderController.java
package com.example.foodapp.controller;

import com.example.foodapp.model.*;
import com.example.foodapp.util.Cart;
import com.example.foodapp.repository.CouponRepository;
import com.example.foodapp.repository.CouponRedemptionRepository;
import com.example.foodapp.service.*;
import com.example.foodapp.util.CartItem;
import com.example.foodapp.util.SessionCart;
import com.stripe.model.Refund;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
    private final PaymentService paymentService;
    private final PaypalService paypalService;
    private final StripeService stripeService;
    private final LoyaltyService loyaltyService;


    public OrderController(OrderService orderService,
                           AddressService addressService,
                           InventoryService inventoryService,
                           EmailService emailService,
                           SessionCart sessionCart,
                           CouponRepository couponRepository,
                           GiftCardService giftCardService,
                           CouponRedemptionRepository couponRedemptionRepository, PaymentService paymentService, PaypalService paypalService, StripeService stripeService, LoyaltyService loyaltyService) {
        this.orderService = orderService;
        this.addressService = addressService;
        this.inventoryService = inventoryService;
        this.emailService = emailService;
        this.sessionCart = sessionCart;
        this.couponRepository = couponRepository;
        this.giftCardService = giftCardService;
        this.couponRedemptionRepository = couponRedemptionRepository;
        this.paymentService = paymentService;
        this.paypalService = paypalService;
        this.stripeService = stripeService;
        this.loyaltyService = loyaltyService;
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
        m.addAttribute("walletPoints", loyaltyService.getBalance(user.getId()));


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
                        @RequestParam(required = false, defaultValue = "0") Integer pointsUsed,
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

        // ✅ Loyalty redeem (wallet points) - add-on logic
        if (pointsUsed != null && pointsUsed > 0) {

            // 1) Do not allow points with gift cards
            boolean cartHasGiftCard = sessionCart.containsGiftCard();
            if (cartHasGiftCard) {
                return "redirect:/checkout?error=Wallet+points+cannot+be+used+with+gift+cards";
            }

            // 2) Do not allow points with coupon (optional rule - keep if you want)
            if (sessionCart.getAppliedCoupon() != null && effectiveDiscount.compareTo(BigDecimal.ZERO) > 0) {
                return "redirect:/checkout?error=Use+either+coupon+or+wallet+points,+not+both";
            }

            // 3) Redeem points -> returns discount amount ($)
            BigDecimal loyaltyDiscount = loyaltyService.redeem(user.getId(), saved.getId(), pointsUsed);

            // 4) Update order discount + totals
            BigDecimal newDiscount = effectiveDiscount.add(loyaltyDiscount);

            saved.setDiscount(newDiscount);
            saved.setTotal(saved.getGrandTotal());
            saved = orderService.save(saved);
        }


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
       // emailService.sendOrderConfirmation(saved.getId());
        inventoryService.applyOrder(saved);
       // emailService.sendOrderSurveyEmail(saved);
        m.addAttribute("cart", sessionCart);

        return "redirect:/payment/checkout?orderId=" + saved.getId();
    }





    // =====================================================
//  RETURN SINGLE ITEM
//  URL: POST /orders/{orderId}/returnItem/{itemId}
// =====================================================


    // =====================================================
    // ✅ RETURN SINGLE ITEM
    // =====================================================
    @PostMapping("/{orderId}/returnItem/{itemId}")
    public String requestItemReturn(@PathVariable Long orderId,
                                    @PathVariable Long itemId,
                                    HttpSession session,
                                    RedirectAttributes ra) {

        User user = currentUser(session);
        if (user == null) return "redirect:/login";

        Order order = orderService.findById(orderId);
        if (order == null) throw new IllegalArgumentException("Order not found");

        try {
            assertOrderBelongsToUser(order, user);
            assertReturnEligibleStatus(order);
            assertWithinReturnWindow(order, 30);

            OrderItem item = order.getItems().stream()
                    .filter(it -> it.getId().equals(itemId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Order item not found"));

            if (Boolean.TRUE.equals(item.getReturnRequested()) || Boolean.TRUE.equals(item.getReturned())) {
                throw new IllegalStateException("Return already requested");
            }

            BigDecimal refundAmount = item.getLineTotal() != null ? item.getLineTotal() : BigDecimal.ZERO;
            if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Refund amount is zero for this item");
            }

            item.setReturnRequested(true);
            if (order.getRefundTotal() == null) order.setRefundTotal(BigDecimal.ZERO);
            order.setRefundTotal(order.getRefundTotal().add(refundAmount));
            order.setStatus("RETURN_REQUESTED");
            orderService.save(order);

            // ✅ 1) normal successful payment lookup
            Payment originalCharge = paymentService.findLatestSuccessfulCharge(orderId);

            // ✅ 2) if not found, try to SYNC from Stripe using latest payment
            if (originalCharge == null) {
                Payment latest = paymentService.findLatestByOrderId(orderId);
                if (latest != null && "STRIPE".equalsIgnoreCase(latest.getProvider())) {
                    stripeService.syncPaymentIfSucceeded(latest);
                }
                originalCharge = paymentService.findLatestSuccessfulCharge(orderId);
            }

            if (originalCharge == null) {
                ra.addFlashAttribute("msg",
                        "Return requested. No successful online payment found to refund (COD/manual).");
                return "redirect:/orders/" + orderId;
            }

            Payment refundRecord = paymentService.createRefundRecord(
                    order, originalCharge, refundAmount, "Refund for item " + item.getProductName()
            );

            String refundId = null;
            String provider = originalCharge.getProvider();

            if ("STRIPE".equalsIgnoreCase(provider)) {
                refundId = stripeService.refundStripePayment(originalCharge.getTransactionId(), refundAmount);
            } else if ("PAYPAL".equalsIgnoreCase(provider)) {
                refundId = paypalService.refundPaypalPayment(originalCharge.getTransactionId(), refundAmount);
            }

            if (refundId != null) {
                paymentService.markRefundCompleted(refundRecord, refundId, order);
                ra.addFlashAttribute("msg", "Refund processed successfully. Confirmation email sent.");
            } else {
                ra.addFlashAttribute("msg", "Return requested. Refund pending (no gateway refund id).");
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            ra.addFlashAttribute("err", ex.getMessage());
        }

        return "redirect:/orders/" + orderId;
    }

    // =====================================================
    // ✅ RETURN FULL ORDER
    // =====================================================
    @PostMapping("/{orderId}/return")
    public String requestOrderReturn(@PathVariable Long orderId,
                                     HttpSession session,
                                     RedirectAttributes ra) {

        User user = currentUser(session);
        if (user == null) return "redirect:/login";

        Order order = orderService.findById(orderId);
        if (order == null) throw new IllegalArgumentException("Order not found");

        try {
            assertOrderBelongsToUser(order, user);
            assertReturnEligibleStatus(order);
            assertWithinReturnWindow(order, 30);

            if ("RETURN_REQUESTED".equals(order.getStatus()) || "RETURNED".equals(order.getStatus())) {
                throw new IllegalStateException("Return already requested");
            }

            BigDecimal refundAmount = BigDecimal.ZERO;
            for (OrderItem it : order.getItems()) {
                if (!Boolean.TRUE.equals(it.getReturned())) {
                    it.setReturnRequested(true);
                    if (it.getLineTotal() != null) refundAmount = refundAmount.add(it.getLineTotal());
                }
            }

            if (order.getRefundTotal() == null) order.setRefundTotal(BigDecimal.ZERO);
            order.setRefundTotal(order.getRefundTotal().add(refundAmount));
            order.setStatus("RETURN_REQUESTED");
            orderService.save(order);

            Payment originalCharge = paymentService.findLatestSuccessfulCharge(orderId);

            if (originalCharge == null) {
                Payment latest = paymentService.findLatestByOrderId(orderId);
                if (latest != null && "STRIPE".equalsIgnoreCase(latest.getProvider())) {
                    stripeService.syncPaymentIfSucceeded(latest);
                }
                originalCharge = paymentService.findLatestSuccessfulCharge(orderId);
            }

            if (originalCharge == null) {
                ra.addFlashAttribute("msg",
                        "Return requested. No successful online payment found to refund (COD/manual).");
                return "redirect:/orders/" + orderId;
            }

            Payment refundRecord = paymentService.createRefundRecord(order, originalCharge, refundAmount, "Full order refund");

            String refundId = null;
            try {
                if ("STRIPE".equalsIgnoreCase(originalCharge.getProvider())) {
                    refundId = stripeService.refundStripePayment(originalCharge.getTransactionId(), refundAmount);
                } else if ("PAYPAL".equalsIgnoreCase(originalCharge.getProvider())) {
                    refundId = paypalService.refundPaypalPayment(originalCharge.getTransactionId(), refundAmount);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                paymentService.markRefundFailed(refundRecord, ex.getMessage());
            }

            if (refundId != null) {
                paymentService.markRefundCompleted(refundRecord, refundId, order);
                ra.addFlashAttribute("msg", "Return requested and refund started. You'll receive a confirmation email.");
            } else {
                ra.addFlashAttribute("msg", "Return requested. Refund could not be started (check logs).");
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            ra.addFlashAttribute("err", ex.getMessage());
        }

        return "redirect:/orders/" + orderId;
    }








    // -------------------------------------------------------
    // validation helpers
    // -------------------------------------------------------







    private static String nvl(String v, String fb) {
        return (v == null || v.isBlank()) ? fb : v;
    }

    private static String joinNonBlank(String... parts) {
        return java.util.Arrays.stream(parts)
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining(" "));
    }


    private static final Set<String> RETURN_ELIGIBLE_STATUSES =
            Set.of("PAID", "SHIPPED", "DELIVERED");

    private void assertOrderBelongsToUser(Order order, User user) {
        if (order == null) {
            throw new IllegalArgumentException("Order not found");
        }
        if (!order.getUserId().equals(user.getId())) {
            throw new IllegalStateException("You are not allowed to modify this order");
        }
    }

    private void assertReturnEligibleStatus(Order order) {
        if (!RETURN_ELIGIBLE_STATUSES.contains(order.getStatus())) {
            throw new IllegalStateException("Order is not eligible for returns in status " + order.getStatus());
        }
    }

    private void assertWithinReturnWindow(Order order, int days) {
        if (order.getCreatedAt() == null) return; // be lenient if missing
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        if (order.getCreatedAt().isBefore(cutoff)) {
            throw new IllegalStateException("Return window has expired for this order");
        }
    }




}
