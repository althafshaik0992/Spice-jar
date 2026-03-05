package com.example.foodapp.controller;

import com.example.foodapp.model.Order;
import com.example.foodapp.service.EmailService;
import com.example.foodapp.service.OrderService;
import com.example.foodapp.service.TrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderActionsController {

    private final OrderService orderService;
    private final TrackingService trackingService;
    private final EmailService emailService;

    @GetMapping("/{id}/track")
    public String trackOrder(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Order order = orderService.findById(id);
        if (order == null) return "redirect:/orders";

        if (order.getTrackingNumber() == null || order.getTrackingNumber().isBlank()) {
            ra.addFlashAttribute("err", "Tracking is not available yet. It will appear once the order ships.");
            return "redirect:/orders/" + id;
        }

        model.addAttribute("order", order);
        model.addAttribute("events", trackingService.getTimeline(order)); // should call FedEx API inside
        return "order_track"; // templates/order_track.html
    }

    /**
     * Marks an order as shipped AND stores tracking info.
     * You can call this from an admin UI button.
     */
    @PostMapping("/{id}/markShipped")
    public String markShipped(
            @PathVariable Long id,
            @RequestParam(defaultValue = "FEDEX") String carrier,
            @RequestParam String trackingNumber,
            @RequestParam(required = false) String shippingService,
            RedirectAttributes ra
    ) {
        Order o = orderService.findById(id);
        if (o == null) return "redirect:/orders";

        if (trackingNumber == null || trackingNumber.isBlank()) {
            ra.addFlashAttribute("err", "Tracking number is required to mark shipped.");
            return "redirect:/orders/" + id;
        }

        o.setStatus("SHIPPED");
        o.setCarrier(carrier.toUpperCase()); // FEDEX / UPS
        o.setTrackingNumber(trackingNumber.trim());
        if (shippingService != null && !shippingService.isBlank()) {
            o.setShippingService(shippingService.trim());
        }

        orderService.save(o);

        ra.addFlashAttribute("msg", "Order marked as shipped. Tracking number saved.");
        return "redirect:/orders/" + id;
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
        orderService.markReturnRequested(id, null);
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

    @GetMapping("/{id}/email")
    public String sendEmailGet(@PathVariable Long id, RedirectAttributes ra) {
        try {
            emailService.sendOrderConfirmation(id);
            ra.addFlashAttribute("msg", "Email receipt sent.");
        } catch (Exception e) {
            ra.addFlashAttribute("err", "Failed to send email: " + e.getMessage());
        }
        return "redirect:/orders/" + id;
    }

    @PostMapping("{id}/email")
    public String sendEmailPost(@PathVariable Long id, RedirectAttributes ra) {
        try {
            emailService.sendOrderConfirmation(id);
            ra.addFlashAttribute("msg", "Email receipt sent.");
        } catch (Exception e) {
            ra.addFlashAttribute("err", "Failed to send email: " + e.getMessage());
        }
        return "redirect:/orders/" + id;
    }
}
