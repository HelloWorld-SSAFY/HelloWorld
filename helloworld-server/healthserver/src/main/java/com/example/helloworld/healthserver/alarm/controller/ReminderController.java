package com.example.helloworld.healthserver.alarm.controller;

import com.example.helloworld.healthserver.alarm.dto.CalendarEventMessage;
import com.example.helloworld.healthserver.alarm.dto.CancelReminderRequest;
import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/internal/reminders") // 내부 서비스 호출용 경로
@RequiredArgsConstructor
//calendar-server가 호출할 내부 API 엔드포인트
public class ReminderController {

    private final Scheduler scheduler; // db-scheduler 주입
    private final Task<CalendarEventMessage> fcmReminderTaskBean; // Step 1-3에서 만든 Task Bean

    @PostMapping("/schedule")
    public ResponseEntity<Void> scheduleReminder(@RequestBody CalendarEventMessage message) {
        log.info("Received a request to schedule a reminder: {}", message);

        // 작업 ID를 고유하게 만들어 중복 예약을 방지
        String instanceId = String.format("fcm-reminder-%d-%d", message.userId(), message.notifyAt().toEpochMilli());

        // db-scheduler에 작업 예약
        scheduler.schedule(
                fcmReminderTaskBean.instance(instanceId, message),
                message.notifyAt() // 캘린더 서버가 보내준 바로 그 시간을 사용
        );

        log.info("Successfully scheduled FCM reminder for user {} at {}", message.userId(), message.notifyAt());
        return ResponseEntity.ok().build();
    }


    /**
     * [추가된 메소드] 예약된 알림을 취소합니다.
     */
    @PostMapping("/cancel")
    public ResponseEntity<Void> cancelReminder(@RequestBody CancelReminderRequest request) {
        log.info("Received a request to cancel a reminder: {}", request);

        // 예약 시 사용했던 고유 ID를 동일한 규칙으로 재생성
        String instanceId = String.format("fcm-reminder-%d-%d", request.userId(), request.notifyAt().toEpochMilli());

        try {
            // db-scheduler에 해당 인스턴스의 취소를 요청
            scheduler.cancel(fcmReminderTaskBean.instance(instanceId));
            log.info("Successfully cancelled scheduled reminder for instanceId: {}", instanceId);
        } catch (Exception e) {
            // 이미 실행되었거나, 예약된 적이 없는 등 취소할 수 없는 경우.
            // 이는 시스템 오류가 아니므로 경고 로그만 남깁니다.
            log.warn("Could not cancel reminder for instanceId: {}. It might have already run or was never scheduled. Error: {}", instanceId, e.getMessage());
        }

        return ResponseEntity.ok().build();
    }
}