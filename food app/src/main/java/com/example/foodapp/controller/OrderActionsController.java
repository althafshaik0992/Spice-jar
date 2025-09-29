package com.example.foodapp.controller;



import com.example.foodapp.model.Order;
import com.example.foodapp.service.OrderService;
import com.example.foodapp.service.TrackingService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDateTime;

@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderActionsController {

    private final OrderService orderService;
    private final TrackingService trackingService;

    @GetMapping("/{id}/track")
    public String trackOrder(@PathVariable Long id, Model model) {
        Order order = orderService.findById(id);
        if (order == null) return "redirect:/orders";

        model.addAttribute("order", order);
        model.addAttribute("events", trackingService.getTimeline(order));
        return "order_track"; // order_track.html must exist under templates/
    }

    /** Example: add a tracking event when you mark the order as shipped */
    @PostMapping("/{id}/markShipped")
    public String markShipped(@PathVariable Long id) {
        Order o = orderService.findById(id);
        if (o == null) return "redirect:/orders";
        o.setStatus("SHIPPED");
        orderService.save(o);

        trackingService.addEvent(id, new com.example.model.TrackingEvent(
                "Shipped", LocalDateTime.now(), "Carrier has picked up your package."
        ));

        return "redirect:/orders/" + id + "/track";
    }


    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes ra) {
        Order o = orderService.findById(id);
        if (o == null) {
            ra.addFlashAttribute("error", "Order not found.");
            return "redirect:/orders";
        }
        if (!orderService.canCancel(o)) {
            ra.addFlashAttribute("error", "This order can no longer be cancelled.");
            return "redirect:/orders/" + id;
        }
        orderService.markCancelled(id);
        ra.addFlashAttribute("success", "Order cancelled.");
        return "redirect:/orders/" + id;
    }

    @PostMapping("/{id}/return")
    public String returnWhole(@PathVariable Long id, RedirectAttributes ra) {
        Order o = orderService.findById(id);
        if (o == null) {
            ra.addFlashAttribute("error", "Order not found.");
            return "redirect:/orders";
        }
        if (!orderService.canReturn(o)) {
            ra.addFlashAttribute("error", "Return window closed or order not delivered yet.");
            return "redirect:/orders/" + id;
        }
        orderService.markReturnRequested(id, null); // null = whole order
        ra.addFlashAttribute("success", "Return requested. We’ll email you instructions.");
        return "redirect:/orders/" + id;
    }

    @PostMapping("/{orderId}/return-item/{productId}")
    public String returnItem(@PathVariable Long orderId,
                             @PathVariable Long productId,
                             RedirectAttributes ra) {
        Order o = orderService.findById(orderId);
        if (o == null) {
            ra.addFlashAttribute("error", "Order not found.");
            return "redirect:/orders";
        }
        if (!orderService.canReturn(o)) {
            ra.addFlashAttribute("error", "Return window closed or order not delivered yet.");
            return "redirect:/orders/" + orderId;
        }
        orderService.markReturnRequested(orderId, productId);
        ra.addFlashAttribute("success", "Item return requested.");
        return "redirect:/orders/" + orderId;
    }

    @GetMapping("/{id}/invoice")
    public void invoice(@PathVariable Long id, HttpServletResponse resp, RedirectAttributes ra) throws IOException {
        Order o = orderService.findById(id);
        if (o == null) {
            resp.sendRedirect("/orders");
            return;
        }
        // Demo PDF (plain text stream). Replace with real PDF generation.
        resp.setContentType("application/pdf");
        resp.setHeader("Content-Disposition", "attachment; filename=invoice-" + id + ".pdf");
        byte[] pdfBytes = orderService.generateInvoicePdf(o); // implement a simple stub
        resp.getOutputStream().write(pdfBytes);
        resp.flushBuffer();
    }
}

