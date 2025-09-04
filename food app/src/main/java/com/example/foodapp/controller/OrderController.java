package com.example.foodapp.controller;

import com.example.foodapp.model.Order;
import com.example.foodapp.model.OrderItem;
import com.example.foodapp.service.OrderService;
import com.example.foodapp.util.Cart;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/order")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/checkout")
    public String checkout(Model m, HttpSession session) {
        var user = session.getAttribute("USER");
        if (user == null) return "redirect:/login";
        Cart cart = (Cart) session.getAttribute("CART");
        if (cart == null || cart.isEmpty()) return "redirect:/cart/view";
        m.addAttribute("cart", cart);
        return "checkout";
    }

    @PostMapping("/place")
    public String placeOrder(@RequestParam String customerName,
                             @RequestParam String address,
                             HttpSession session,
                             Model m) {

        Object sessionUser = session.getAttribute("USER");
        if (sessionUser == null) return "redirect:/login";

        Cart cart = (Cart) session.getAttribute("CART");
        if (cart == null || cart.isEmpty()) return "redirect:/cart/view";

        Long userId = extractUserId(sessionUser);
        if (userId == null) return "redirect:/login";

        Order order = new Order();
        order.setUserId(userId);
        order.setCustomerName(customerName);
        order.setAddress(address);
        order.setCreatedAt(LocalDateTime.now());

        // If your Cart already has subtotal/tax/grandTotal, prefer copying those:
        // order.setTotal(cart.getSubtotal());
        // order.setTax(cart.getTax());
        // order.setGrandTotal(cart.getGrandTotal());

        // If not, keep your existing total as "subtotal" and compute tax/grand:
        order.setTotal(cart.getTotal()); // treat as subtotal
        order.setItems(cart.getItems().stream().map(ci -> {
            OrderItem oi = new OrderItem();
            oi.setProductId(ci.getProductId());
            oi.setProductName(ci.getName());
            oi.setQuantity(ci.getQty());
            oi.setPrice(ci.getPrice());
            return oi;
        }).collect(Collectors.toList()));

        // ⬅️ compute 8% tax + grand total
        //orderService.applyTaxAndGrandTotal(order);

        orderService.save(order);

        cart.clear();
        m.addAttribute("order", order);
        m.addAttribute("uuid", java.util.UUID.randomUUID());
        return "order_success";
    }


    /** Helper to get the user id from whatever object you put in session under "USER". */
    private Long extractUserId(Object sessionUser) {
        if (sessionUser == null) return null;

        // If you have a concrete User class, prefer casting:
        // return ((User) sessionUser).getId();

        // Generic (works even if it's a proxy or a different type with getId()):
        try {
            var m = sessionUser.getClass().getMethod("getId");
            Object id = m.invoke(sessionUser);
            if (id instanceof Number) return ((Number) id).longValue();
        } catch (Exception ignored) {}
        return null;
    }

}
