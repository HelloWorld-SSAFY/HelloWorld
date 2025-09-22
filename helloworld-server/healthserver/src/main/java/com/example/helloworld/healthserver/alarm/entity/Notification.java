package com.example.helloworld.healthserver.alarm.entity;

import com.example.helloworld.healthserver.alarm.domain.AlarmType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alarm_id")
    private Long alarmId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alarm_type", nullable = false, length = 32)
    private AlarmType alarmType;


    // ✅ userserver 엔티티 연관 대신, FK 값만 보관
    @Column(name = "couple_id", nullable = false)
    private Long coupleId;

    @Column(name = "alarm_title", nullable = false, length = 255)
    private String alarmTitle;

    @Column(name = "alarm_msg")
    private String alarmMsg;

    @Column(name = "created_at", nullable = false, updatable = false)
    private java.sql.Timestamp createdAt;
}

