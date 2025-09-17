package com.example.helloworld.healthserver.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "fetal_movements",
        indexes = {
                @Index(name = "idx_fm_couple_time", columnList = "couple_id, recorded_at DESC")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FetalMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "couple_id", nullable = false)
    private Long coupleId;

    @Column(name = "notes", length = 500)
    private String notes;

    // DB 기본값 now() 사용. 필요 시 앱에서 명시적으로 설정 가능
    @Column(name = "recorded_at")
    private Instant recordedAt;
}