package com.example.foodapp.service;

import com.example.foodapp.model.FedExTrackResponse;
import com.example.foodapp.model.Order;
import com.example.foodapp.model.TrackingEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TrackingService {

    private final FedExTrackClient fedExTrackClient;

    public List<TrackingEvent> getTimeline(Order order) {

        if (order == null ||
                order.getTrackingNumber() == null ||
                order.getTrackingNumber().isBlank()) {
            return List.of();
        }

        String carrier = order.getCarrier() == null
                ? ""
                : order.getCarrier().trim().toUpperCase();

        if ("FEDEX".equals(carrier)) {
            try {
                return fedexTimeline(order.getTrackingNumber());
            } catch (Exception ex) {
                // Avoid 500 error page
                return List.of(new TrackingEvent(
                        null,
                        "TRACKING_UNAVAILABLE",
                        safe(ex.getMessage()),
                        ""
                ));
            }
        }

        return List.of();
    }

    private List<TrackingEvent> fedexTimeline(String trackingNumber) {

        FedExTrackResponse resp = fedExTrackClient.trackByTrackingNumber(trackingNumber);

        List<TrackingEvent> out = new ArrayList<>();
        if (resp == null || resp.getOutput() == null) return out;

        FedExTrackResponse.Output output = resp.getOutput();
        if (output.getCompleteTrackResults() == null) return out;

        output.getCompleteTrackResults().forEach(result -> {
            if (result == null || result.getTrackResults() == null) return;

            // ✅ trackResults is a LIST now
            result.getTrackResults().forEach(tr -> {
                if (tr == null || tr.getScanEvents() == null) return;

                tr.getScanEvents().forEach(event -> {
                    if (event == null) return;

                    LocalDateTime when = null;
                    try {
                        String dt = event.getDate();
                        if (dt != null && !dt.isBlank()) {
                            when = OffsetDateTime.parse(dt).toLocalDateTime();
                        }
                    } catch (Exception ignored) {}

                    String location = "";
                    if (event.getLocation() != null) {
                        String city = safe(event.getLocation().getCity());
                        String st = safe(event.getLocation().getStateOrProvinceCode());
                        location = city + (!st.isBlank() ? ", " + st : "");
                    }

                    out.add(new TrackingEvent(
                            when,
                            safe(event.getEventType()),
                            safe(event.getEventDescription()),
                            location
                    ));
                });
            });
        });

        out.sort(
                Comparator.comparing(
                        TrackingEvent::getWhen,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed()
        );

        return out;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
