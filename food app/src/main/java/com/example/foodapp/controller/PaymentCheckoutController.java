// src/main/java/com/example/foodapp/controller/PaymentCheckoutController.java
package com.example.foodapp.controller;

import com.example.foodapp.model.Order;
import com.example.foodapp.model.Payment;
import com.example.foodapp.model.User;
import com.example.foodapp.service.*;
import com.example.foodapp.util.SessionCart;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/payment")
public class PaymentCheckoutController extends BaseController {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PaypalService paypalService;
    private final StripeService stripeService;
    private final SessionCart cart;
    private final GiftCardService giftCardService;
    private final EmailService emailService;

    public PaymentCheckoutController(OrderService orderService,
                                     PaymentService paymentService,
                                     PaypalService paypalService,
                                     StripeService stripeService,
                                     SessionCart cart,
                                     GiftCardService giftCardService,
                                     EmailService emailService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.paypalService = paypalService;
        this.stripeService = stripeService;
        this.cart = cart;
        this.giftCardService = giftCardService;
        this.emailService = emailService;
    }

    @Value("${app.paypal.currency:USD}")
    private String paypalCurrency;

    // ======================================
    //  CHECKOUT PAGE
    // ======================================
    @GetMapping("/checkout")
    public String checkout(@RequestParam("orderId") Long orderId,
                           HttpSession session,
                           Model model) {

        User user = currentUser(session);
        if (user == null) return "redirect:/login";

        Order order = orderService.findById(orderId);
        if (order == null) return "redirect:/cart/view";

        model.addAttribute("order", order);
        model.addAttribute("cartCount", 0);

        model.addAttribute("giftBalance", giftCardService.usableBalanceForUser(user.getId()));
        model.addAttribute("giftApplied", order.getGiftAppliedSafe());
        model.addAttribute("remainingToPay", order.getRemainingToPay());

        return "payment";
    }

    // ======================================
    //  PAYPAL
    // ======================================
    @PostMapping("/paypal/start")
    @ResponseBody
    public Map<String, Object> startPaypal(@RequestBody Map<String, Object> body, HttpSession session) {
        if (session.getAttribute("USER") == null) return Map.of("ok", false);

        Long orderId = ((Number) body.get("orderId")).longValue();
        var out = paypalService.createOrder(orderId, paypalCurrency);

        return Map.of("ok", out.ok(), "approvalUrl", out.approvalUrl(), "paymentId", out.paymentId());
    }

    @GetMapping("/paypal/return")
    public String paypalReturn(@RequestParam String token,
                               @RequestParam(required = false) String PayerID) {

        var ok = paypalService.capture(token);
        Long orderId = ok.orderId();

        if (ok.ok()) {
            // PayPal capture should already mark the Payment row SUCCEEDED in paypalService.capture(...)
            orderService.markPaid(orderId);
            return "redirect:/payment/success?orderId=" + orderId + "&paymentId=" + ok.paymentId();
        }

        return "redirect:/orders";
    }

    // ======================================
    //  STRIPE – WALLET / CARD FORM (PaymentIntent)
    // ======================================
    @PostMapping("/stripe/start")
    @ResponseBody
    public Map<String, Object> startStripe(@RequestBody Map<String, Object> body, HttpSession session) {

        if (session.getAttribute("USER") == null) {
            return Map.of("status", "error", "message", "Not authenticated");
        }

        Long orderId = ((Number) body.get("orderId")).longValue();

        // MUST return: clientSecret + paymentIntentId + paymentId
        Map<String, Object> out = stripeService.createWalletIntent(orderId);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("status", "success");
        res.put("clientSecret", out.get("clientSecret"));
        res.put("paymentIntentId", out.get("paymentIntentId"));
        res.put("paymentId", out.get("paymentId"));
        return res;
    }

    /**
     * ✅ CONFIRM endpoint: updates DB so refunds work
     *
     * Body: { orderId, paymentId, paymentIntentId }
     */
    @PostMapping(
            path = "/stripe/confirm",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseBody
    public Map<String, Object> stripeConfirm(@RequestBody Map<String, Object> body, HttpSession session) {

        if (session.getAttribute("USER") == null) {
            return Map.of("status", "error", "message", "Not authenticated");
        }

        Long orderId = Long.valueOf(String.valueOf(body.get("orderId")));
        Long paymentId = Long.valueOf(String.valueOf(body.get("paymentId")));
        String piId = String.valueOf(body.get("paymentIntentId"));

        if (piId == null || !piId.startsWith("pi_")) {
            return Map.of("status", "error", "message", "Invalid paymentIntentId");
        }

        // Optional but recommended: verify with Stripe server-side
        if (!stripeService.isPaymentIntentSucceeded(piId)) {
            return Map.of("status", "error", "message", "Stripe says payment is not succeeded yet");
        }

        // 1) Mark payment SUCCEEDED + store pi id for refunds
        paymentService.markSucceededByPaymentId(paymentId, piId, piId, "STRIPE");

        // 2) Mark order PAID
        orderService.markPaid(orderId);

        return Map.of("status", "success");
    }

    // ======================================
    //  STRIPE CHECKOUT SESSION RETURN (Legacy)
    // ======================================
    @GetMapping("/stripe/return")
    public String stripeReturn(@RequestParam Long orderId,
                               @RequestParam(required = false) String session_id) {

        try {
            if (session_id != null && stripeService.verifySession(session_id)) {

                orderService.markPaid(orderId);

                Payment p = paymentService.findByProviderPaymentId(session_id);
                if (p != null) {
                    String piId = stripeService.getPaymentIntentIdFromSession(session_id);
                    if (piId != null) {
                        paymentService.markSucceededByProviderPaymentId(session_id, piId, "STRIPE");
                    }
                }

                Long paymentId = (p != null ? p.getId() : null);
                return "redirect:/payment/success?orderId=" + orderId +
                        (paymentId != null ? ("&paymentId=" + paymentId) : "");
            }

            return "redirect:/orders";

        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/orders";
        }
    }

    // ======================================
    //  SUCCESS PAGE
    // ======================================
    @GetMapping("/success")
    public String success(@RequestParam Long orderId,
                          @RequestParam(required = false) Long paymentId,
                          HttpSession session,
                          Model m) {

        Order order = orderService.findById(orderId);

        Payment payment = (paymentId != null)
                ? paymentService.findById(paymentId)
                : paymentService.findLatestByOrderId(orderId);

        if (order != null && "PAID".equalsIgnoreCase(order.getStatus())) {
            if (!Boolean.TRUE.equals(order.getConfirmationSent())) {
                emailService.sendOrderConfirmation(order.getId());
                emailService.sendOrderSurveyEmail(order);
                order.setConfirmationSent(true);
                orderService.save(order);
            }
        }

        cart.clear(session);

        m.addAttribute("order", order);
        m.addAttribute("payment", payment);
        m.addAttribute("cartCount", 0);

        return "order_success";
    }

    // ======================================
    //  STRIPE WEBHOOK (optional)
    // ======================================
    @PostMapping("/stripe/webhook")
    public ResponseEntity<String> stripeWebhook(
            @RequestHeader(value = "Stripe-Signature", required = false) String sig,
            @RequestBody String payload) {

        stripeService.handleStripeWebhook(payload, sig);
        return ResponseEntity.ok("ok");
    }
}
