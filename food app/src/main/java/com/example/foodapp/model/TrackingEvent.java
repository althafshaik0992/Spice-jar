package com.example.model;

import java.time.LocalDateTime;

public class TrackingEvent {
    private String title;
    private LocalDateTime time;
    private String note;

    public TrackingEvent(String title, LocalDateTime time, String note) {
        this.title = title;
        this.time = time;
        this.note = note;
    }

    public String getTitle() { return title; }
    public LocalDateTime getTime() { return time; }
    public String getNote() { return note; }
}
