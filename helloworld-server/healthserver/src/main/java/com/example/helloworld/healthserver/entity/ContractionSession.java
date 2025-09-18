package com.example.helloworld.healthserver.entity;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "contraction_sessions",
        indexes = @Index(name="idx_cs_couple_time", columnList="couple_id, start_time DESC"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor
@Builder
public class ContractionSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="couple_id", nullable=false)
    private Long coupleId;

    @Column(name="start_time", nullable=false)
    private Instant startTime;

    @Column(name="end_time", nullable=false)
    private Instant endTime;

    @Column(name="interval_min")
    private Integer intervalMin;

    @Column(name="duration")
    private Integer durationSec;

    @Builder.Default
    @Column(name="alert_sent", nullable=false)
    private boolean alertSent = false;

    @CreationTimestamp
    @Column(name="created_at", updatable=false)
    private Instant createdAt;

    public void markAlertSent() {
        this.alertSent = true;
    }

    // 필요하면 해제용도
    public void unmarkAlertSent() {
        this.alertSent = false;
    }

    /** start/end 기준으로 durationSec(초) 계산 */
    public void fillDerived() {
        if (this.startTime == null || this.endTime == null) {
            this.durationSec = null;
            return;
        }
        long sec = Duration.between(this.startTime, this.endTime).getSeconds();
        this.durationSec = (sec < 0) ? null : (int) sec;
    }

    /** 직전 세션의 end 시각으로부터 interval(분) 계산 */
    public void setIntervalFromPrev(Instant prevEnd) {
        if (prevEnd == null || this.startTime == null) {
            this.intervalMin = null;
            return;
        }
        long diffSec = Duration.between(prevEnd, this.startTime).getSeconds();
        this.intervalMin = (int) Math.max(0, Math.round(diffSec / 60.0));
    }
}

