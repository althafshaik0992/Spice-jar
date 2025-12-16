//package com.example.foodapp.controller;
//
//import com.example.foodapp.model.Order;
//import com.example.foodapp.service.OrderService;
//import com.example.foodapp.service.PaymentService;
//import jakarta.servlet.http.HttpSession;
//import org.springframework.stereotype.Controller;
//import org.springframework.ui.Model;
//import org.springframework.web.bind.annotation.*;
//
//@Controller
//@RequestMapping("/admin/refunds")
//public class AdminRefundController extends BaseController {
//
//    private final OrderService orderService;
//    private final PaymentService paymentService;
//
//    public AdminRefundController(OrderService orderService, PaymentService paymentService) {
//        this.orderService = orderService;
//        this.paymentService = paymentService;
//    }
//
//    @GetMapping("/{orderId}")
//    public String viewRefunds(@PathVariable Long orderId,
//                              HttpSession session,
//                              Model model) {
//
//        // ✅ put your admin auth check here (example)
//        if (session.getAttribute("ADMIN_USER") == null) {
//            return "redirect:/login";
//        }
//
//        Order order = orderService.findById(orderId);
//        if (order == null) return "redirect:/admin/orders";
//
//        var view = paymentService.getAdminRefundView(orderId);
//
//        model.addAttribute("order", order);
//        model.addAttribute("original", view.originalPayment());
//        model.addAttribute("refunds", view.refundRows());
//
//        return "admin/admin_refund_view";
//    }
//}
