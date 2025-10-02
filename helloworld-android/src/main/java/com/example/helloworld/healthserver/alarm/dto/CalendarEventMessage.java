package com.example.helloworld.healthserver.alarm.dto;

import java.io.Serializable;
import java.time.Instant;

public record CalendarEventMessage(
        Long userId,
        String title,
        String body,
        Instant notifyAt
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
