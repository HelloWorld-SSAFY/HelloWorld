package com.example.helloworld.userserver.alarm.entity;

import jakarta.persistence.*;
import lombok.*;
import java.sql.Timestamp;
import java.time.Instant;

@Entity
@Table(
        name = "notification_recipients",
        uniqueConstraints = {
                @UniqueConstraint(name = "ux_notirec_alarm_user", columnNames = {"alarm_id","recipient_user_id"})
        },
        indexes = {
                @Index(name = "idx_notirec_alarm", columnList = "alarm_id"),
                @Index(name = "idx_notirec_user",  columnList = "recipient_user_id")
        }
)
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationRecipient {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alarm_id", nullable = false)
    private Long alarmId;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Column(name = "status", nullable = false, length = 16)
    private String status; // PENDING / SENT / FAILED

    @Column(name = "message_id", length = 128)
    private String messageId; // (집계 후 대표 메시지ID)

    @Column(name = "fail_reason")
    private String failReason; // (집계 후 대표 실패코드)

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    @Column(name = "sent_at")
    private Timestamp sentAt;

    @PrePersist
    void onCreate(){ if (createdAt==null) createdAt = Timestamp.from(Instant.now()); }

    public void markSent(String msgId) {
        this.status = "SENT";
        this.messageId = msgId;
        this.failReason = null;
        this.sentAt = Timestamp.from(Instant.now());
    }
    public void markFailed(String reason) {
        this.status = "FAILED";
        this.failReason = reason;
    }
    public void markPending() {                 // ★ 추가
        this.status = "PENDING";
        this.messageId = null;
        this.failReason = null;
        this.sentAt = null;
    }
}


