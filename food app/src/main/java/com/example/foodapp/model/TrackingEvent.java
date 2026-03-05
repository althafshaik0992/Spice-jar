package com.example.foodapp.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class TrackingEvent {
    private LocalDateTime when;
    private String status;
    private String description;
    private String location;
}
