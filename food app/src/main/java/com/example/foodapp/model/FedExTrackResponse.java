package com.example.foodapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FedExTrackResponse {

    private Output output;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Output {
        private List<CompleteTrackResult> completeTrackResults;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CompleteTrackResult {
        // ✅ FedEx returns this as array
        private List<TrackResults> trackResults;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TrackResults {
        private String trackingNumber;

        // ✅ scans
        private List<ScanEvent> scanEvents;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScanEvent {
        private String date;
        private String eventType;
        private String eventDescription;
        private Location location;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Location {
        private String city;
        private String stateOrProvinceCode;
        private String countryCode;
    }
}
