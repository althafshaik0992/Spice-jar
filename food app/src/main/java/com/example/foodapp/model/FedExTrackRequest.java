package com.example.foodapp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
@Data
@AllArgsConstructor
public class FedExTrackRequest {

    private boolean includeDetailedScans;
    private List<TrackingInfo> trackingInfo;

    public static FedExTrackRequest single(String trackingNumber) {
        return new FedExTrackRequest(
                true,
                List.of(
                        new TrackingInfo(
                                new TrackingNumberInfo(trackingNumber)
                        )
                )
        );
    }

    @Data
    @AllArgsConstructor
    public static class TrackingInfo {
        private TrackingNumberInfo trackingNumberInfo;
    }

    @Data
    @AllArgsConstructor
    public static class TrackingNumberInfo {
        private String trackingNumber;
    }
}

