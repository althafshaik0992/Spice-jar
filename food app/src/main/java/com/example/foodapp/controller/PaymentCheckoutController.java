package com.example.foodapp.controller;

import com.example.foodapp.model.Order;
import com.example.foodapp.model.User;
import com.example.foodapp.service.*;
import com.example.foodapp.util.GlobalData;
import com.example.foodapp.util.SessionCart;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.example.foodapp.util.GlobalData.cart;

@Controller
@RequestMapping("/payment")
public class PaymentCheckoutController extends BaseController {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PaypalService paypalService;
    private final StripeService stripeService;
    private final SessionCart cart;
    private final GiftCardService giftCardService;



    public PaymentCheckoutController(OrderService orderService,
                                     PaymentService paymentService,
                                     PaypalService paypalService,
                                     StripeService stripeService, SessionCart cart, GiftCardService giftCardService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.paypalService = paypalService;
        this.stripeService = stripeService;
        this.cart = cart;
        this.giftCardService = giftCardService;
    }

    @Value("${app.paypal.currency:USD}")
    private String paypalCurrency;

    @GetMapping("/checkout")
    public String checkout(@RequestParam("orderId") Long orderId,
                           HttpSession session,
                           Model model) {

        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        Order order = orderService.findById(orderId);
        if (order == null) {
            return "redirect:/cart/view";
        }

        // 1) Gift card balance for this user
        BigDecimal giftBalance = giftCardService.usableBalanceForUser(user.getId());

        // 2) How much has already been applied on this order
        BigDecimal alreadyApplied = order.getGiftAppliedSafe();

        // 3) Remaining amount to pay AFTER gift cards
        BigDecimal remainingToPay = order.getRemainingToPay();

        model.addAttribute("order", order);
        model.addAttribute("cartCount", 0); // or your real cart badge count

        // gift-card–related attributes used by the HTML/JS
        model.addAttribute("giftBalance", giftBalance);
        model.addAttribute("giftApplied", alreadyApplied);
        model.addAttribute("remainingToPay", remainingToPay);

        return "payment";
    }

    @PostMapping(
            path = "/gift/apply",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseBody
    public Map<String, Object> applyGiftBalance(@RequestBody Map<String, Object> body,
                                                HttpSession session) {

        User user = currentUser(session);
        if (user == null) {
            return Map.of(
                    "status", "error",
                    "message", "Please login to use gift cards."
            );
        }

        Long orderId;
        BigDecimal requested;

        try {
            orderId = Long.valueOf(String.valueOf(body.get("orderId")));
            requested = new BigDecimal(String.valueOf(body.get("amount")));
        } catch (Exception e) {
            return Map.of(
                    "status", "error",
                    "message", "Invalid request."
            );
        }

        Order order = orderService.findById(orderId);
        if (order == null) {
            return Map.of(
                    "status", "error",
                    "message", "Order not found."
            );
        }

        // available gift credit for this user
        BigDecimal available = giftCardService.usableBalanceForUser(user.getId());
        if (available.signum() <= 0) {
            return Map.of(
                    "status", "error",
                    "message", "No gift card balance available."
            );
        }

        if (requested.signum() <= 0) {
            return Map.of(
                    "status", "error",
                    "message", "Amount must be greater than zero."
            );
        }

        // You can't apply more than available or more than remaining to pay
        BigDecimal maxAllowed = available.min(order.getRemainingToPay());
        BigDecimal desired = requested.min(maxAllowed);

        if (desired.signum() <= 0) {
            return Map.of(
                    "status", "error",
                    "message", "Nothing to apply for this order."
            );
        }

        // Actually consume from the user's gift cards
        BigDecimal used = giftCardService.consumeBalanceForUser(
                user.getId(),
                desired,
                "Order payment",
                order.getId()
        );

        if (used.signum() <= 0) {
            return Map.of(
                    "status", "error",
                    "message", "Unable to apply gift balance."
            );
        }

        // Increase giftApplied on this order
        BigDecimal newApplied = order.getGiftAppliedSafe().add(used);
        order.setGiftApplied(newApplied);
        orderService.save(order);

        // Compute updated remaining amounts
        BigDecimal newRemainingToPay = order.getRemainingToPay();
        BigDecimal newAvailable = available.subtract(used);
        if (newAvailable.signum() < 0) newAvailable = BigDecimal.ZERO;

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "success");
        resp.put("message", "Applied $" + used + " from your gift balance.");
        resp.put("applied", newApplied);
        resp.put("giftBalanceLeft", newAvailable);
        resp.put("remainingToPay", newRemainingToPay);

        return resp;
    }


    /** COD: mark pending and return 200 */
    @PostMapping("/cod")
    @ResponseBody
    public Map<String,Object> cod(@RequestBody Map<String,Object> body, HttpSession session){
        if (session.getAttribute("USER") == null) return Map.of("ok", false);
        Long orderId = ((Number) body.get("orderId")).longValue();
        paymentService.markCodPending(orderId);
        orderService.markPendingCod(orderId);
        return Map.of("ok", true);
    }

    /** Start PayPal: create order on PayPal and return approval URL */
    @PostMapping("/paypal/start")
    @ResponseBody
    public Map<String,Object> startPaypal(@RequestBody Map<String,Object> body, HttpSession session){
        if (session.getAttribute("USER") == null) return Map.of("ok", false);
        Long orderId = ((Number) body.get("orderId")).longValue();
        var out = paypalService.createOrder(orderId, paypalCurrency);
        // out.approvalUrl is where the browser should go
        return Map.of("ok", out.ok(), "approvalUrl", out.approvalUrl(), "paymentId", out.paymentId());
    }

    /** PayPal return (success) */
    @GetMapping("/paypal/return")
    public String paypalReturn(@RequestParam String token, @RequestParam(required=false) String PayerID){
        // token == PayPal order id
        var ok = paypalService.capture(token);
        Long orderId = ok.orderId();
        if (ok.ok()) {
            orderService.markPaid(orderId);
            return "redirect:/payment/success?orderId=" + orderId + "&paymentId=" + ok.paymentId();
        }
        return "redirect:/orders";
    }

    /** Start Stripe Checkout: create session & return its URL */
    @PostMapping("/stripe/start")
    @ResponseBody
    public Map<String,Object> startStripe(@RequestBody Map<String,Object> body, HttpSession session){
        if (session.getAttribute("USER") == null) return Map.of("ok", false);
        Long orderId = ((Number) body.get("orderId")).longValue();
        var out = stripeService.createCheckoutSession(orderId);
        return Map.of("ok", out.ok(), "checkoutUrl", out.checkoutUrl(), "paymentId", out.paymentId());
    }

    /** Stripe success/cancel return URLs */
    @GetMapping("/stripe/return")
    public String stripeReturn(@RequestParam Long orderId, @RequestParam(required=false) String session_id){
        if (session_id != null && stripeService.verifySession(session_id)) {
            orderService.markPaid(orderId);
            var paymentId = paymentService.findLatestByOrderId(orderId) != null
                    ? paymentService.findLatestByOrderId(orderId).getId()
                    : null;
            return "redirect:/payment/success?orderId=" + orderId + (paymentId!=null?("&paymentId="+paymentId):"");
        }
        return "redirect:/orders";
    }

    /** Success page */
    @GetMapping("/success")
    public String success(@RequestParam Long orderId,
                          @RequestParam(required = false) Long paymentId,
                          Model m){
        var order = orderService.findById(orderId);
        var payment = (paymentId != null) ? paymentService.findById(paymentId) : paymentService.findLatestByOrderId(orderId);
        m.addAttribute("order", order);
        m.addAttribute("payment", payment);
        return "order_success";
    }

    @PostMapping("/wallet/start")
    @ResponseBody
    public Map<String, Object> startWallet(@RequestBody Map<String, Object> body, HttpSession session) {
        if (session.getAttribute("USER") == null) return Map.of("ok", false);
        Long orderId = ((Number) body.get("orderId")).longValue();
        var out = stripeService.createWalletIntent(orderId);
        return Map.of("ok", true, "clientSecret", out.get("clientSecret"), "paymentIntentId", out.get("paymentIntentId"));
    }

    /** Stripe webhook (configure the endpoint URL in your Stripe dashboard) */
    @PostMapping("/stripe/webhook")
    public ResponseEntity<String> stripeWebhook(@RequestHeader(value = "Stripe-Signature", required = false) String sig,
                                                @RequestBody String payload) {
        stripeService.handleStripeWebhook(payload, sig);
        return ResponseEntity.ok("ok");
    }
}