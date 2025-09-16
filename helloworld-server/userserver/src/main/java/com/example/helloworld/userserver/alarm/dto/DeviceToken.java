package com.example.helloworld.userserver.alarm.dto;

import jakarta.persistence.*;
import lombok.*;
import java.sql.Timestamp;

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

    @Column(name="created_at", nullable=false)
    private Timestamp createdAt;

    @Column(name="last_seen_at")
    private Timestamp lastSeenAt;

    public void activate(String platform) {
        this.isActive = true;
        this.lastSeenAt = new Timestamp(System.currentTimeMillis());
    }

    public void deactivate() { this.isActive = false; }
}

