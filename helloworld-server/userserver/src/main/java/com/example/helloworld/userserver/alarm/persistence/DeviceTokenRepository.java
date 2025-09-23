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

    Optional<DeviceToken> findByUserIdAndToken(Long userId, String token);


    @Transactional
    default void upsert(Long userId, String token) {
        var now = new Timestamp(System.currentTimeMillis());
        var existing = this.findAll().stream()
                .filter(t -> t.getUserId().equals(userId) && t.getToken().equals(token))
                .findFirst();
        if (existing.isPresent()) {
            var t = existing.get();//로그임실패시
            t.activate();
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
        // ▼▼▼ [수정] 훨씬 간결하고 효율적으로 수정합니다. ▼▼▼
        this.findByUserIdAndToken(userId, token)
                .ifPresent(DeviceToken::deactivate);
    }


//    @Transactional
//    default void deactivate(Long userId, String token) {
//        this.findAll().stream()
//                .filter(t -> t.getUserId().equals(userId) && t.getToken().equals(token))
//                .forEach(DeviceToken::deactivate);
//    }
}

