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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
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
        return "checkout";
    }

    /** 2) Create the order, then redirect to the unified payment page. */
    @PostMapping("/place")
    public String submit(
            // The @RequestParam names have been updated to match the 'name' attributes in the checkout.html form.
            @RequestParam String customerName,
            @RequestParam String email,
            @RequestParam String street,
            @RequestParam String phone,
            @RequestParam String city,
            @RequestParam String state,
            @RequestParam String zip,
            @RequestParam String country,
            HttpSession session) {

        Object sessionUser = session.getAttribute("USER");
        Long userId = extractUserId(sessionUser);

        Cart cart = (Cart) session.getAttribute("CART");
        if (cart == null || cart.isEmpty()) {
            return "redirect:/cart/view";
        }

        Order o = new Order();
        o.setCreatedAt(LocalDateTime.now());
        o.setUserId(userId);

        // Set the fields on the order object using the corrected request parameters.
        o.setCustomerName(customerName);
        o.setEmail(email);
        o.setPhone(phone);
        o.setStreet(street);
        o.setCity(city);
        o.setState(state);
        o.setZip(zip);
        o.setCountry(country);

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


        o.setStatus("PENDING_PAYMENT");

        // Save
        orderService.save(o);

        // Clear cart
        cart.clear();

        // ➜ Go to payment chooser page
        return "redirect:/payment/checkout?orderId=" + o.getId();
    }



//    @GetMapping("/confirm")
//    public String showOrderConfirmation(@RequestParam Long orderId, Model model) {
//        // Retrieve the order details using the service
//        Order order = orderService.getOrderById(orderId);
//
//        // Concatenate address fields to match the single string required by the HTML template
//        String shippingAddress = String.format("%s, %s, %s %s",
//                order.getStreet(),
//                order.getCity(),
//                order.getState(),
//                order.getZip());
//
//        // Add the order and related data to the model for the template
//        model.addAttribute("order", order);
//        model.addAttribute("subject", "Your Order is Confirmed!");
//        model.addAttribute("preheader", "Thanks for your order — it’s on the way!");
//        model.addAttribute("firstName", order.getCustomerName());
//        model.addAttribute("orderNumber", order.getId());
//        model.addAttribute("status", order.getStatus());
//        model.addAttribute("orderDate", order.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
//        model.addAttribute("shippingAddress", shippingAddress);
//        model.addAttribute("items", order.getItems());
//
//        // Use the derived calculations from the Order model
//        model.addAttribute("subtotal", String.format("$%.2f", order.getSubtotal()));
//        model.addAttribute("tax", String.format("$%.2f", order.getTax()));
//        model.addAttribute("total", String.format("$%.2f", order.getGrandTotal()));
//
//        // Mocks for template variables
//        model.addAttribute("links", Map.of(
//                "home", "/",
//                "orders", "/orders",
//                "help", "/help",
//                "unsub", "/unsubscribe"
//        ));
//        model.addAttribute("assets", Map.of(
//                "logo", "/images/spice-jar-logo.png"
//        ));
//        model.addAttribute("year", java.time.LocalDate.now().getYear());
//
//        // Return the name of the Thymeleaf template
//        return "order_confirmation";
//    }



    private Long extractUserId(Object sessionUser) {
        try {
            var m = sessionUser.getClass().getMethod("getId");
            Object id = m.invoke(sessionUser);
            return (id instanceof Number) ? ((Number) id).longValue() : null;
        } catch (Exception e) { return null; }
    }

    private static BigDecimal nz(BigDecimal v){ return v == null ? BigDecimal.ZERO : v; }
}
