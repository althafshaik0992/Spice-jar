package com.example.foodapp.controller;

import com.example.foodapp.model.Order;
import com.example.foodapp.model.User;
import com.example.foodapp.service.OrderService;
import com.example.foodapp.service.PaymentService;
import com.example.foodapp.service.PaypalService;
import com.example.foodapp.service.StripeService;
import com.example.foodapp.util.GlobalData;
import com.example.foodapp.util.SessionCart;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
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



    public PaymentCheckoutController(OrderService orderService,
                                     PaymentService paymentService,
                                     PaypalService paypalService,
                                     StripeService stripeService, SessionCart cart) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.paypalService = paypalService;
        this.stripeService = stripeService;
        this.cart = cart;
    }

    @Value("${app.paypal.currency:USD}")
    private String paypalCurrency;

    @GetMapping("/checkout")
    public String page(@RequestParam Long orderId, HttpSession session, Model m){

        User user = currentUser(session);
        if (user == null) return "redirect:/login";
        Order order = orderService.findById(orderId);
        if (order == null) return "redirect:/orders";
        // optional: ensure belongs to current user

        m.addAttribute("order", order);
        return "payment"; // the HTML above
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