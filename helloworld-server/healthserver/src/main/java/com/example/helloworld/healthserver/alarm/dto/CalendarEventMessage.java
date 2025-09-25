package com.example.helloworld.healthserver.alarm.dto;

import java.time.Instant;

public record CalendarEventMessage(
        Long userId,      // 알림을 받을 사용자 ID
        String title,     // 일정 알림 제목 (예: "일정 알림")
        String body,      // 일정 내용 (예: "2시 병원 예약")
        Instant notifyAt  // 알림을 보내야 할 정확한 시간 (UTC)
) {}