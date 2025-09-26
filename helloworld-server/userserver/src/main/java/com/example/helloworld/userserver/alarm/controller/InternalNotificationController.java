package com.example.helloworld.userserver.alarm.controller;

import com.example.helloworld.userserver.alarm.entity.NotificationRecipient;
import com.example.helloworld.userserver.alarm.persistence.NotificationRecipientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/notifications")
@RequiredArgsConstructor
public class InternalNotificationController {
    private final NotificationRecipientRepository repo;

    // 헬스서버가 집계 결과만 전달
    public record UpsertReq(Long alarmId, Long userId, String status, String messageId, String failReason) {}

    @PostMapping("/recipients/upsert")
    public ResponseEntity<Void> upsertRecipient(@RequestBody UpsertReq req) {
        var rec = repo.findByAlarmIdAndRecipientUserId(req.alarmId(), req.userId())
                .orElseGet(() -> NotificationRecipient.builder()
                        .alarmId(req.alarmId())
                        .recipientUserId(req.userId())
                        .status("PENDING")
                        .build());
        if ("SENT".equalsIgnoreCase(req.status())) {
            rec.markSent(req.messageId());
        } else if ("FAILED".equalsIgnoreCase(req.status())) {
            rec.markFailed(req.failReason());
        } else {
            rec.markPending(); // 필요 시 setter 추가 or builder 교체
        }
        repo.save(rec);
        return ResponseEntity.noContent().build();
    }
}

