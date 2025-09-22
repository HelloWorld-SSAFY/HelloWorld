package com.example.helloworld.userserver.alarm.persistence;

import com.example.helloworld.userserver.alarm.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.sql.Timestamp;
import java.util.List;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    List<DeviceToken> findByUserIdAndIsActiveTrue(Long userId);

    // ✅ 최신 활성 토큰 1개
    Optional<DeviceToken> findFirstByUserIdAndIsActiveTrueOrderByLastSeenAtDescCreatedAtDesc(Long userId);

    @Transactional
    default void upsert(Long userId, String token, String platform) {
        var now = new Timestamp(System.currentTimeMillis());
        var existing = this.findAll().stream()
                .filter(t -> t.getUserId().equals(userId) && t.getToken().equals(token))
                .findFirst();
        if (existing.isPresent()) {
            var t = existing.get();
            t.activate(platform);
        } else {
            this.save(DeviceToken.builder()
                    .userId(userId)
                    .token(token)
                    .isActive(true)
                    .createdAt(now)
                    .lastSeenAt(now)
                    .build());
        }
    }

    @Transactional
    default void deactivate(Long userId, String token) {
        this.findAll().stream()
                .filter(t -> t.getUserId().equals(userId) && t.getToken().equals(token))
                .forEach(DeviceToken::deactivate);
    }
}

