package com.example.helloworld.userserver.alarm.entity;

import jakarta.persistence.*;
import lombok.*;
import java.sql.Timestamp;
import java.time.Instant;

@Entity
@Table(name = "device_tokens",
        uniqueConstraints = @UniqueConstraint(name="ux_device_token", columnNames={"user_id","token"}))
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeviceToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="user_id", nullable=false)
    private Long userId;

    @Column(name="token", nullable=false, columnDefinition="TEXT")
    private String token;

    @Column(name="is_active", nullable=false)
    private boolean isActive;

    @Column(name="platform")
    private Instant platform;

    @Column(name="created_at", nullable=false)
    private String  createdAt;

    @Column(name="last_seen_at")
    private Instant lastSeenAt;

    public void activate() {
        this.isActive = true;
        this.lastSeenAt = Instant.now();
    }

    public void deactivate() { this.isActive = false; }
}

