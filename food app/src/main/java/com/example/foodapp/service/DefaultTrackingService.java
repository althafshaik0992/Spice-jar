package com.example.foodapp.service;

import com.example.foodapp.model.Order;
import com.example.model.TrackingEvent;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DefaultTrackingService implements TrackingService {

    // Simple in-memory storage; swap with a repository later
    private final Map<Long, List<TrackingEvent>> store = new ConcurrentHashMap<>();

    @Override
    public List<TrackingEvent> getTimeline(Order order) {
        if (order == null) return List.of();

        // Start with any persisted/runtime events you’ve stored
        List<TrackingEvent> events = new ArrayList<>(store.getOrDefault(order.getId(), List.of()));

        // Generate default milestones based on createdAt + status
        LocalDateTime t0 = Optional.ofNullable(order.getCreatedAt()).orElse(LocalDateTime.now().minusHours(1));

        // Base milestones
        List<TrackingEvent> defaults = new ArrayList<>(List.of(
                new TrackingEvent("Order placed", t0, "We received your order.")
               // new TrackingEvent("Processing", t0.plusHours(2), "Your items are being prepared.")
        ));

        // Only add later steps if the status suggests we’ve progressed
        String status = Optional.ofNullable(order.getStatus()).orElse("").toUpperCase(Locale.ROOT);

        // PENDING_PAYMENT / PENDING_COD => stop at Processing
        if (status.equals("PAID") || status.equals("SHIPPED") || status.equals("OUT_FOR_DELIVERY") || status.equals("DELIVERED")) {
            defaults.add(new TrackingEvent("Paid", t0.plusHours(3), "Payment confirmed."));
        }
        if (status.equals("SHIPPED") || status.equals("OUT_FOR_DELIVERY") || status.equals("DELIVERED")) {
            defaults.add(new TrackingEvent("Shipped", t0.plusDays(1), "Package handed to the carrier."));
        }
        if (status.equals("OUT_FOR_DELIVERY") || status.equals("DELIVERED")) {
            defaults.add(new TrackingEvent("Out for delivery", t0.plusDays(2), "Courier is on the way."));
        }
        if (status.equals("DELIVERED")) {
            defaults.add(new TrackingEvent("Delivered", t0.plusDays(2).plusHours(5), "Package delivered successfully."));
        }
        if (status.equals("CANCELLED")) {
            defaults.add(new TrackingEvent("Cancelled", t0.plusHours(1), "Your order has been cancelled."));
        }
        if (status.equals("RETURN_REQUESTED") || status.equals("RETURNED")) {
            defaults.add(new TrackingEvent("Return requested", t0.plusDays(3), "We received your return request."));
        }
        if (status.equals("RETURNED")) {
            defaults.add(new TrackingEvent("Returned", t0.plusDays(4), "Return completed and processed."));
        }

        // Merge defaults + stored and sort chronologically
        events.addAll(defaults);
        events.sort(Comparator.comparing(TrackingEvent::getTime));

        return events;
    }

    @Override
    public void addEvent(Long orderId, TrackingEvent event) {
        if (orderId == null || event == null) return;
        store.compute(orderId, (id, list) -> {
            List<TrackingEvent> l = (list == null) ? new ArrayList<>() : new ArrayList<>(list);
            l.add(event);
            l.sort(Comparator.comparing(TrackingEvent::getTime));
            return l;
        });
    }

    @Override
    public void setEvents(Long orderId, List<TrackingEvent> events) {
        if (orderId == null) return;
        List<TrackingEvent> copy = (events == null) ? new ArrayList<>() : new ArrayList<>(events);
        copy.sort(Comparator.comparing(TrackingEvent::getTime));
        store.put(orderId, copy);
    }
}
