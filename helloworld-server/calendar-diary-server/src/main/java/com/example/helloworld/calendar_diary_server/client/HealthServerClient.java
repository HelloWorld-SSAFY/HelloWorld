package com.example.helloworld.calendar_diary_server.client;

import com.example.helloworld.calendar_diary_server.dto.CalendarEventMessage;
import com.example.helloworld.calendar_diary_server.dto.CancelReminderRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

// name은 healthserver의 application.name, url은 healthserver의 주소
@FeignClient(name = "health-server", url = "${healthserver.base-url}")
public interface HealthServerClient {

    @PostMapping("/api/internal/reminders/schedule")
    void scheduleReminder(@RequestBody CalendarEventMessage message);


    /**
     * health-server에 알림 취소를 요청
     */
    @PostMapping("/api/internal/reminders/cancel")
    void cancelReminder(@RequestBody CancelReminderRequest request);
}