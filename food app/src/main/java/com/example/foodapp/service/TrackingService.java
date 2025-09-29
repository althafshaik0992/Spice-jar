package com.example.foodapp.service;

import com.example.foodapp.model.Order;
import com.example.model.TrackingEvent;

import java.util.List;

public interface TrackingService {

    /** Full timeline for an order (stored + generated defaults). */
    List<TrackingEvent> getTimeline(Order order);

    /** Add a custom tracking event (e.g., when you mark it shipped). */
    void addEvent(Long orderId, TrackingEvent event);

    /** Replace all events for an order (optional helper). */
    void setEvents(Long orderId, List<TrackingEvent> events);
}
