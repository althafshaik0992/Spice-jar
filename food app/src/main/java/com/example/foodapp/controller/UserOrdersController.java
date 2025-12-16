package com.example.foodapp.controller;

import com.example.foodapp.model.Order;
import com.example.foodapp.model.Payment;
import com.example.foodapp.model.User;
import com.example.foodapp.service.OrderService;
import com.example.foodapp.service.PaymentService;
import com.example.foodapp.service.UserOrderService;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/orders")   // ✅ all mappings in this controller are under /orders/...
public class UserOrdersController extends BaseController {

    private final UserOrderService userOrderService;
    private final OrderService orderService;
    private final PaymentService paymentService;

    public UserOrdersController(UserOrderService userOrderService,
                                OrderService orderService,
                                PaymentService paymentService) {
        this.userOrderService = userOrderService;
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    // ============================
    //  MY ORDERS LIST
    //  GET /orders
    // ============================
    @GetMapping
    public String myOrders(@RequestParam(required = false) String q,
                           @RequestParam(required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           HttpSession session,
                           Model m) {

        User user = currentUser(session);
        if (user == null) return "redirect:/login";

        Long userId = user.getId();
        if (userId == null) return "redirect:/login";

        // Load + filter user orders
        List<Order> all = userOrderService.findByUser(userId);
        List<Order> filtered = userOrderService.filterUserOrders(all, q, from, to);

        m.addAttribute("orders", filtered);
        return "order";   // <- your list page (order.html)
    }

    // ============================
    //  ORDER DETAILS
    //  GET /orders/{id}
    // ============================
    @GetMapping("/{id}")
    public String showOrder(@PathVariable Long id,
                            HttpSession session,
                            Model model) {

        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        // use real OrderService to fetch single order
        Order order = orderService.findById(id);
        if (order == null) {
            return "redirect:/orders";
        }

        assertOrderBelongsToUser(order, user);

        // Refund history for this order
        List<Payment> refunds = paymentService.findRefundsForOrder(order.getId()); // ✅
        model.addAttribute("refunds", refunds);

        model.addAttribute("order", order);
        return "order-details";   // <- your details page
    }




    // ============================
    //  HELPERS
    // ============================

    /** Ensure the order belongs to the logged-in user */
    private void assertOrderBelongsToUser(Order order, User user) {
        if (order == null) {
            throw new IllegalArgumentException("Order not found");
        }
        if (order.getUserId() == null || !order.getUserId().equals(user.getId())) {
            throw new IllegalStateException("You are not allowed to access this order");
        }
    }
}
