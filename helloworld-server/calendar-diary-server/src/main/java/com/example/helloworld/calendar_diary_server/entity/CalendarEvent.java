package com.example.helloworld.calendar_diary_server.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "calendarevents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id", updatable = false, nullable = false)
    private Long eventId;

    @Column(name = "couple_id", nullable = false)
    private Long coupleId;     // MSA 분리 → 소프트 FK

    @Column(name = "writer_id")
    private Long writerId;     // 선택

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(length = 1000)
    private String memo;

    @Column(name = "order_no")
    private Integer orderNo;

    @Column(name = "is_remind", nullable = false)
    private boolean isRemind = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}