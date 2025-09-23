package com.example.helloworld.userserver.alarm.persistence;

import com.example.helloworld.userserver.alarm.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
        var now = Instant.now();
        
        this.findByUserIdAndToken(userId, token)
                .ifPresentOrElse(
                        // 토큰이 이미 존재하면 activate만 호출 (lastSeenAt 업데이트)
                        DeviceToken::activate,
                        // 토큰이 없으면 새로 생성하여 저장
                        () -> {
                            this.save(DeviceToken.builder()
                                    .userId(userId)
                                    .token(token)
                                    .isActive(true)
                                    .createdAt(now) // 이제 타입이 Instant로 일치합니다.
                                    .lastSeenAt(now)  // 이제 타입이 Instant로 일치합니다.
                                    .build());
                        }
                );
    }
    @Transactional
    default void deactivate(Long userId, String token) {
        // 훨씬 간결하고 효율적으로 수정
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

