package com.example.helloworld.healthserver.alarm.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.time.Instant;

@Entity
@Table(name = "notification_recipients",
        indexes = {
                @Index(name = "idx_notirec_alarm", columnList = "alarm_id"),
                @Index(name = "idx_notirec_user",  columnList = "recipient_user_id")
        })
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
    private String messageId;

    @Column(name = "fail_reason")
    private String failReason;

    @Column(name = "sent_at")
    private Timestamp sentAt;

    public void markSent(String msgId) {
        this.status = "SENT";
        this.messageId = msgId;
        this.sentAt = Timestamp.from(Instant.now());
    }
    public void markFailed(String reason) {
        this.status = "FAILED";
        this.failReason = reason;
    }
}

