package com.example.helloworld.userserver.alarm.controller;

import com.example.helloworld.userserver.alarm.entity.NotificationRecipient;
import com.example.helloworld.userserver.alarm.persistence.NotificationRecipientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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
    @Transactional
    public ResponseEntity<Void> upsertRecipient(@RequestBody UpsertReq req) {
        try {
            var rec = repo.findByAlarmIdAndRecipientUserId(req.alarmId(), req.userId())
                    .orElseGet(() -> NotificationRecipient.builder()
                            .alarmId(req.alarmId())
                            .recipientUserId(req.userId())
                            .status("PENDING")
                            .build());

            applyStatus(rec, req.status(), req.messageId(), req.failReason());
            repo.saveAndFlush(rec);
        } catch (DataIntegrityViolationException e) {
            // 동시성으로 insert 충돌 → 다시 읽어서 update
            var rec = repo.findByAlarmIdAndRecipientUserId(req.alarmId(), req.userId())
                    .orElseThrow(); // 이제는 반드시 존재
            applyStatus(rec, req.status(), req.messageId(), req.failReason());
            repo.save(rec);
        }
        return ResponseEntity.noContent().build();
    }

    private void applyStatus(NotificationRecipient rec, String status, String msgId, String reason) {
        String s = status == null ? "" : status.toUpperCase();
        if ("SENT".equals(s)) rec.markSent(msgId);
        else if ("FAILED".equals(s)) rec.markFailed(reason);
        else rec.markPending();
    }
}

