package com.example.helloworld.calendar_diary_server.dto;

import java.time.Instant;

/**
 * 캘린더 서버에서 수신하는 알림 예약 이벤트 메시지
 */
public record CalendarEventMessage(
        Long userId,      // 알림을 받을 사용자 ID
        String title,     // 일정 알림 제목 (예: "일정 알림")
        String body,      // 일정 내용 (예: "2시 병원 예약")
        Instant notifyAt  // 알림을 보내야 할 정확한 시간 (UTC)
) {}