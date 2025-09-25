package com.example.helloworld.healthserver.alarm.dto;

import java.time.Instant;

public record CancelReminderRequest(
        Long userId,
        Instant notifyAt) {
}
