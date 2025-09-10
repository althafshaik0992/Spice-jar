// src/main/java/com/example/foodapp/controller/OrderController.java
package com.example.foodapp.controller;

import com.example.foodapp.model.Order;
import com.example.foodapp.model.OrderItem;
import com.example.foodapp.service.OrderService;
import com.example.foodapp.util.Cart;
import com.example.foodapp.util.CartItem;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/order")
public class OrderController {

    private final OrderService orderService;
    public OrderController(OrderService orderService) { this.orderService = orderService; }

    /** 1) Show the shipping/checkout form (cart summary is on the right). */
    @GetMapping("/checkout")
    public String checkout(Model m, HttpSession session) {
        Object user = session.getAttribute("USER");
        if (user == null) return "redirect:/login";

        Cart cart = (Cart) session.getAttribute("CART");
        if (cart == null || cart.isEmpty()) return "redirect:/cart/view";

        m.addAttribute("cart", cart);
        return "checkout"; // the shipping form page (your template) :contentReference[oaicite:2]{index=2}
    }

    /** 2) Create the order, then redirect to the unified payment page. */
    @PostMapping("/place")
    public String placeOrder(@RequestParam String customerName,
                             @RequestParam String email,
                             @RequestParam String phone,
                             @RequestParam String street,
                             @RequestParam String city,
                             @RequestParam String state,
                             @RequestParam String zip,
                             @RequestParam String country,
                             HttpSession session) {

        Object sessionUser = session.getAttribute("USER");
        if (sessionUser == null) return "redirect:/login";

        Cart cart = (Cart) session.getAttribute("CART");
        if (cart == null || cart.isEmpty()) return "redirect:/cart/view";

        Long userId = extractUserId(sessionUser);
        if (userId == null) return "redirect:/login";

        // Build the order
        Order o = new Order();
        o.setUserId(userId);
        o.setCustomerName(customerName);
        o.setEmail(email);
        o.setPhone(phone);
        o.setStreet(street);
        o.setCity(city);
        o.setState(state);
        o.setZip(zip);
        o.setCountry(country);
        // optional combined address for legacy fields
        o.setAddress(street + ", " + city + ", " + state + " " + zip + ", " + country);

        o.setCreatedAt(LocalDateTime.now());

        // Map items
        o.setItems(cart.getItems().stream().map(ci -> {
            OrderItem it = new OrderItem();
            it.setProductId(ci.getProductId());
            it.setProductName(ci.getName());
            it.setQuantity(ci.getQty());
            it.setPrice(ci.getPrice());
            return it;
        }).collect(Collectors.toList()));

        // Totals (prefer cart if present)
        BigDecimal subtotal = nz(cart.getSubtotal());
        if (subtotal.signum() == 0) {
            // fallback: sum items
            subtotal = cart.getItems().stream()
                    .map(CartItem::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        BigDecimal tax = subtotal.multiply(new BigDecimal("0.08"))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal grand = subtotal.add(tax).setScale(2, RoundingMode.HALF_UP);

        // If your Order has fields: subtotal/tax/grandTotal – set them:

        o.setStatus("NEW");

        // Save
        orderService.save(o);

        // Clear cart
        cart.clear();

        // ➜ Go to payment chooser page
        return "redirect:/payment/checkout?orderId=" + o.getId();
    }

    private Long extractUserId(Object sessionUser) {
        try {
            var m = sessionUser.getClass().getMethod("getId");
            Object id = m.invoke(sessionUser);
            return (id instanceof Number) ? ((Number) id).longValue() : null;
        } catch (Exception e) { return null; }
    }

    private static BigDecimal nz(BigDecimal v){ return v == null ? BigDecimal.ZERO : v; }
}
