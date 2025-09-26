package com.example.helloworld.userserver.alarm.persistence;

import com.example.helloworld.userserver.alarm.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.sql.Timestamp;
import java.util.List;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    List<DeviceToken> findByUserIdAndIsActiveTrue(Long userId);

    // ✅ 최신 활성 토큰 1개
    Optional<DeviceToken> findFirstByUserIdAndIsActiveTrueOrderByLastSeenAtDescCreatedAtDesc(Long userId);

    Optional<DeviceToken> findByUserIdAndToken(Long userId, String token);

    // 플랫폼이 여러 별칭 중 하나인 최신 활성 토큰 1개
    Optional<DeviceToken>
    findFirstByUserIdAndPlatformInAndIsActiveTrueOrderByLastSeenAtDescCreatedAtDesc(
            Long userId, Collection<String> platform
    );


    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO device_tokens(user_id, token, platform, is_active, created_at, last_seen_at)
        VALUES (:userId, :token, :platform, TRUE, now(), now())
        ON CONFLICT ON CONSTRAINT ux_device_token
        DO UPDATE SET
          is_active = TRUE,
          platform = EXCLUDED.platform,
          last_seen_at = now()
        """, nativeQuery = true)
    void upsert(@Param("userId") Long userId,
                @Param("token") String token,
                @Param("platform") String platform);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE device_tokens
           SET is_active = FALSE, last_seen_at = now()
         WHERE user_id = :userId AND token = :token
        """, nativeQuery = true)
    void deactivate(@Param("userId") Long userId, @Param("token") String token);


//    @Transactional
//    default void deactivate(Long userId, String token) {
//        this.findAll().stream()
//                .filter(t -> t.getUserId().equals(userId) && t.getToken().equals(token))
//                .forEach(DeviceToken::deactivate);
//    }
}

