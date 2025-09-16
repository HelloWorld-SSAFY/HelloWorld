package com.example.helloworld.healthserver.entity;


import jakarta.persistence.Entity;
import lombok.*;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@Entity
@Table(
        name = "maternal_health",
        indexes = {
                @Index(name = "idx_mh_couple_day", columnList = "couple_id, record_date DESC")
        }
)
public class MaternalHealth {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "couple_id", nullable = false)
    private Long coupleId;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    // NUMERIC(5,2)
    @Column(name = "weight", precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(name = "max_blood_pressure")
    private Integer maxBloodPressure;

    @Column(name = "min_blood_pressure")
    private Integer minBloodPressure;

    @Column(name = "blood_sugar")
    private Integer bloodSugar;

    @Column(name = "created_at")
    private Instant createdAt;    // DB default now()

    @Column(name = "updated_at")
    private Instant updatedAt;    // DB default now()
}
