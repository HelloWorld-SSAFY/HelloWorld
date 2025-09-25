package com.example.helloworld.calendar_diary_server.dto;

import java.time.Instant;

public record CancelReminderRequest(
        Long userId,
        Instant notifyAt
) {
}
