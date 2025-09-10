// src/main/java/com/example/foodapp/controller/PaymentCheckoutController.java
package com.example.foodapp.controller;

import com.example.foodapp.model.Order;
import com.example.foodapp.model.Payment;
import com.example.foodapp.service.OrderService;
import com.example.foodapp.service.PaymentService;
import com.example.foodapp.service.PaypalService;
import com.example.foodapp.service.StripeService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequestMapping("/payment")
public class PaymentCheckoutController {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final StripeService stripeService;
    private final PaypalService paypalService;

    public PaymentCheckoutController(OrderService orderService,
                                     PaymentService paymentService,
                                     StripeService stripeService,
                                     PaypalService paypalService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.stripeService = stripeService;
        this.paypalService = paypalService;
    }

    @Value("${paypal.client.id}")
    private String paypalClientId;
    @Value("${paypal.currency:USD}")
    private String paypalCurrency;

    /** 3) Unified payment page (Card / PayPal / COD). */
    @GetMapping("/checkout")
    public String paymentCheckout(@RequestParam(required = false) Long orderId,
                                  HttpSession session,
                                  Model m) {
        if (session.getAttribute("USER") == null) return "redirect:/login";
        Long userId = extractUserId(session.getAttribute("USER"));
        if (userId == null) return "redirect:/login";

        com.example.foodapp.model.Order order;
        if (orderId == null) {
            order = orderService.findLatestForUser(userId); // implement in OrderService/Repository
            if (order == null) return "redirect:/cart/view";
            return "redirect:/payment/checkout?orderId=" + order.getId();
        } else {
            order = orderService.findById(orderId);
            if (order == null) return "redirect:/orders";
            if (!userId.equals(order.getUserId())) return "redirect:/orders";
        }

        m.addAttribute("order", order);
        m.addAttribute("paypalClientId", paypalClientId);
        m.addAttribute("paypalCurrency", paypalCurrency);
        return "payment"; // your payment.html
    }


    /** Stripe: create PaymentIntent + save Payment row */
    @PostMapping("/init/stripe")
    @ResponseBody
    public Map<String, Object> initStripe(@RequestBody Map<String, Object> req) {
        Long orderId = ((Number) req.get("orderId")).longValue();
        Order order = orderService.findById(orderId);

        Payment p = paymentService.start(order.getId(), order.getGrandTotal(), "USD", "STRIPE");
        var init = stripeService.createPaymentIntent(p); // {clientSecret, publishableKey, stripePaymentIntentId}
        // store provider id
        paymentService.attachProviderPaymentId(p.getId(), init.get("stripePaymentIntentId"));

        return Map.of(
                "clientSecret", init.get("clientSecret"),
                "publishableKey", init.get("publishableKey"),
                "paymentId", p.getId()
        );
    }

    /** PayPal: create order on PayPal and attach provider id */
    @PostMapping("/paypal/create")
    @ResponseBody
    public Map<String, Object> paypalCreate(@RequestBody Map<String, Object> req) throws Exception {
        Long orderId = ((Number) req.get("orderId")).longValue();
        Order order = orderService.findById(orderId);

        Payment p = paymentService.start(order.getId(), order.getGrandTotal(), "USD", "PAYPAL");
        var create = paypalService.createOrder(p); // returns {id, status}
        paymentService.attachProviderPaymentId(p.getId(), create.get("id"));
        return Map.of("id", create.get("id"));
    }

    /** PayPal: capture and mark success */
    @PostMapping("/paypal/capture")
    @ResponseBody
    public Map<String, Object> paypalCapture(@RequestBody Map<String, Object> req) throws Exception {
        String paypalOrderId = (String) req.get("paypalOrderId");
        var cap = paypalService.captureOrder(paypalOrderId); // {status, captureId, amount}

        Payment p = paymentService.markSucceededByProviderPaymentId(
                paypalOrderId, cap.get("captureId"), "PAYPAL");

        orderService.markPaid(p.getOrderId());
        return Map.of("ok", true, "paymentId", p.getId());
    }

    /** COD: mark pending and keep order pending */
    @PostMapping("/cod")
    public ResponseEntity<?> cod(@RequestBody Map<String, Object> req) {
        Long orderId = ((Number) req.get("orderId")).longValue();
        Order order = orderService.findById(orderId);

        Payment p = paymentService.start(order.getId(), order.getGrandTotal(), "USD", "COD");
        paymentService.markPendingCod(p.getId());
        orderService.markPendingCod(orderId);

        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Success page (optional): reuse your existing template */
    @GetMapping("/success")
    public String success(@RequestParam Long orderId,
                          @RequestParam(required = false) Long paymentId,
                          Model m) {
        var order = orderService.findById(orderId);
        var payment = (paymentId != null)
                ? paymentService.findById(paymentId)
                : paymentService.findLatestByOrderId(orderId);
        m.addAttribute("order", order);
        m.addAttribute("payment", payment);
        return "payment_success";
    }

    // ---- helpers ----
    private Long extractUserId(Object sessionUser) {
        try {
            var m = sessionUser.getClass().getMethod("getId");
            Object id = m.invoke(sessionUser);
            return (id instanceof Number) ? ((Number) id).longValue() : null;
        } catch (Exception e) { return null; }
    }
}
