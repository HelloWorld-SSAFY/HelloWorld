package com.example.helloworld.calendar_diary_server.client;

import com.example.helloworld.calendar_diary_server.dto.CalendarEventMessage;
import com.example.helloworld.calendar_diary_server.dto.CancelReminderRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

// name은 healthserver의 application.name, url은 healthserver의 주소
@FeignClient(name = "health-server", url = "${healthserver.base-url}")
public interface HealthServerClient {

    @PostMapping(value = "/api/internal/reminders/schedule")
    void scheduleReminder(
            @RequestHeader("X-Internal-User-Id") Long userId,
            @RequestHeader("X-Internal-Couple-Id") Long coupleId,
            @RequestHeader(value = "X-App-Token", required = false) String appToken,
            @RequestBody CalendarEventMessage message
    );

    /**
     * health-server에 알림 취소를 요청
     */
    @PostMapping(value = "/api/internal/reminders/cancel")
    void cancelReminder(
            @RequestHeader("X-Internal-User-Id") Long userId,
            @RequestHeader("X-Internal-Couple-Id") Long coupleId,
            @RequestHeader(value = "X-App-Token", required = false) String appToken,
            @RequestBody CancelReminderRequest request
    );
}
