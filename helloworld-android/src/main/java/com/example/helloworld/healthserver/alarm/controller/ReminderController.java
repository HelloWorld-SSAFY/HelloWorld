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
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Slf4j
@RestController
@RequestMapping("/api/internal/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final Scheduler scheduler;
    private final Task<CalendarEventMessage> fcmReminderTaskBean;

    // ✅ true면 캘린더가 보내는 notifyAt을 "KST 벽시계"로 간주하여 UTC Instant로 변환
    @Value("${reminder.kst-wall-input:true}")
    private boolean kstWallInput;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @PostMapping("/schedule")
    public ResponseEntity<Void> scheduleReminder(@RequestBody CalendarEventMessage message) {
        // 예약 식별자(취소 시 동일 규칙으로 사용) — 기존 유지
        String instanceId = String.format("fcm-reminder-%d-%d",
                message.userId(), message.notifyAt().toEpochMilli());

        // 🔧 시각 보정: KST 벽시계를 UTC Instant로 변환(토글 가능)
        Instant runAt = kstWallInput ? toUtcFromKstWall(message.notifyAt())
                : message.notifyAt();

        log.info("[REMINDER] schedule req userId={} rawNotifyAt(UTC)={} rawNotifyAt(KST)={} -> runAt(UTC)={} runAt(KST)={}",
                message.userId(),
                message.notifyAt(),
                message.notifyAt().atZone(KST),
                runAt,
                runAt.atZone(KST));

        // db-scheduler에 작업 예약
        scheduler.schedule(
                fcmReminderTaskBean.instance(instanceId, message),
                runAt
        );

        log.info("[REMINDER] scheduled instanceId={} userId={} at(UTC)={} at(KST)={}",
                instanceId, message.userId(), runAt, runAt.atZone(KST));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/cancel")
    public ResponseEntity<Void> cancelReminder(@RequestBody CancelReminderRequest request) {
        String instanceId = String.format("fcm-reminder-%d-%d",
                request.userId(), request.notifyAt().toEpochMilli());
        try {
            scheduler.cancel(fcmReminderTaskBean.instance(instanceId));
            log.info("[REMINDER] cancelled instanceId={}", instanceId);
        } catch (Exception e) {
            log.warn("[REMINDER] cancel miss instanceId={} (already-run or never-scheduled): {}",
                    instanceId, e.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    /**
     * ⚠️ 'raw' Instant를 UTC로 보지 않고, "그 시각 숫자"를 KST 벽시계로 재해석하여
     * 실제 UTC Instant로 변환. (예: 09:00 '숫자'를 +09:00에 놓고 UTC로 환산)
     */
    private Instant toUtcFromKstWall(Instant raw) {
        // raw를 "UTC 시계에서 본 LocalDateTime"으로 꺼낸 뒤, 그 숫자를 KST 벽시계로 간주
        LocalDateTime wall = LocalDateTime.ofInstant(raw, ZoneOffset.UTC);
        return wall.atZone(KST).toInstant();
    }
}