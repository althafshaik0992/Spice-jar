package com.example.foodapp.controller;



import com.example.foodapp.model.Order;
import com.example.foodapp.repository.OrderRepository;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/shipping")
public class AdminShippingController {

    private final OrderRepository orderRepository;

    public AdminShippingController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @PostMapping("/ship/{orderId}")
    public ResponseEntity<?> markShipped(
            @PathVariable Long orderId,
            @RequestBody ShipRequest req
    ) {
        Order order = orderRepository.findById(orderId).orElseThrow();

        order.setCarrier(req.getCarrier());                 // "FEDEX" or "UPS"
        order.setTrackingNumber(req.getTrackingNumber());
        order.setShippingService(req.getShippingService()); // optional
        order.setStatus("SHIPPED");
        order.setShippedAt(LocalDateTime.now());

        orderRepository.save(order);
        return ResponseEntity.ok().build();
    }

    @Data
    public static class ShipRequest {
        @NotBlank private String carrier;         // FEDEX / UPS
        @NotBlank private String trackingNumber;
        private String shippingService;           // e.g. "FEDEX_GROUND"
    }
}
