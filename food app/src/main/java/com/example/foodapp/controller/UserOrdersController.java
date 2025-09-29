package com.example.foodapp.controller;

import com.example.foodapp.model.Order;
import com.example.foodapp.service.OrderService;
import com.example.foodapp.service.UserOrderService;
import com.example.foodapp.service.UserOrderServiceImpl;

import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Controller
public class UserOrdersController {

    private final UserOrderService orderService;

    private final OrderService orderService1;

    public UserOrdersController(UserOrderService orderService, OrderService orderService1) {
        this.orderService = orderService;
        this.orderService1 = orderService1;
    }

    @GetMapping("/orders")
    public String myOrders(@RequestParam(required = false) String q,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           HttpSession session,
                           Model m) {

        Object sessionUser = session.getAttribute("USER");
        if (sessionUser == null) return "redirect:/login";

        Long userId = extractUserId(sessionUser);
        if (userId == null) return "redirect:/login";

        List<Order> all = orderService.findByUser(userId);
        List<Order> filtered = orderService.filterUserOrders(all, q, from, to);



        m.addAttribute("orders", filtered);


        return "order"; // <-- match your HTML file name
    }

    @GetMapping("/orders/{id}")
    public String myOrderDetail(@PathVariable Long id, HttpSession session, Model m) {
        Object sessionUser = session.getAttribute("USER");
        if (sessionUser == null) return "redirect:/login";

        Long userId = extractUserId(sessionUser);
        if (userId == null) return "redirect:/login";

        Order o = orderService.findById(id);
        if (o == null || o.getUserId() == null || !o.getUserId().equals(userId)) {
            return "redirect:/orders";
        }
        m.addAttribute("order", o);

        return "order-details"; // create a simple detail page if you want
    }

    private Long extractUserId(Object sessionUser) {
        try {
            var m = sessionUser.getClass().getMethod("getId");
            Object id = m.invoke(sessionUser);
            if (id instanceof Number) return ((Number) id).longValue();
        } catch (Exception ignore) {
        }
        return null;
    }
}
