package com.example.helloworld.healthserver.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "health_data",
        indexes = {
                @Index(name = "idx_hd_couple_date", columnList = "couple_id, date DESC")
        }
)
public class HealthData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "health_id")
    private Long healthId;                 // PK

    @Column(name = "couple_id", nullable = false)
    private Long coupleId;                 // 커플아이디 (필수라고 가정)

    // KST 등 타임존은 서비스/DB 설정에 따름. Instant로 저장(UTC 권장).
    @Column(name = "date")
    private Instant date;                  // 측정 시각 (nullable 허용)

    @Column(name = "stress")
    private Integer stress;                // 스트레스 (nullable)

    @Column(name = "sleep_hours")
    private Integer sleepHours;            // 수면 시간 (nullable)

    @Column(name = "heartrate")
    private Integer heartrate;             // 심박수 (nullable)

    @Column(name = "steps")
    private Integer steps;                 // 걸음수 (nullable)

    @Column(name = "is_danger")
    private Boolean isDanger;              // 위험 여부 (nullable)
}
