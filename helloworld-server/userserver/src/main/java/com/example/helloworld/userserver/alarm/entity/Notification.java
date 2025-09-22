package com.example.helloworld.userserver.alarm.entity;

import com.example.helloworld.userserver.alarm.domain.AlarmType;
import com.example.helloworld.userserver.member.entity.Couple;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "couple_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_notification_couple"))
    private Couple couple;

    @Column(name = "alarm_title", nullable = false, length = 255)
    private String alarmTitle;

    @Column(name = "alarm_msg")
    private String alarmMsg;

    @Column(name = "created_at", nullable = false, updatable = false)
    private java.sql.Timestamp createdAt;
}

