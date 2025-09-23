package com.example.helloworld.healthserver.entity;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;
import java.time.Instant;

@Entity
@Table(
        name = "steps_data",
        indexes = {
                @Index(name = "idx_steps_couple_date", columnList = "couple_id,date"),
                @Index(name = "idx_steps_date", columnList = "date")
        }
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StepsData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "steps_id")
    private Long stepsId;

    @Column(name = "couple_id", nullable = false)
    private Long coupleId;

    // 측정 시점(UTC Instant)
    @Column(name = "date", nullable = false)
    private Instant date;

    @Column(name = "steps")
    private Integer steps;

    @Column(name = "latitude")
    private  Double latitude;

    @Column(name = "longitude")
    private  Double longitude;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;
}
