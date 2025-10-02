package com.example.helloworld.userserver.auth.application;


import com.example.helloworld.userserver.auth.token.TokenHashes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * TokenCacheService
 *
 * - 원문 토큰은 저장하지 않고 sha256Base64 해시만 Redis key로 사용.
 * - token:{hash} -> JSON { active, memberId, coupleId, role, exp }
 * - user_tokens:{memberId} -> set{hash1, hash2, ...}
 * - blacklist:{hash} -> "1" (TTL = remaining lifetime)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenCacheService {

    private final StringRedisTemplate redis;
    private final ObjectMapper om = new ObjectMapper();

    // 안전 캡(초) — 너무 큰 TTL을 막기 위해
    private static final long MAX_CACHE_TTL_SECONDS = 720000000;

    private String tokenKey(String hash) { return "token:" + hash; }
    private String blacklistKey(String hash) { return "blacklist:" + hash; }
    private String userTokensKey(Long memberId) { return "user_tokens:" + memberId; }

    /**
     * Register an access token in Redis.
     * - accessToken: 원문 토큰 (이 메서드에서 해시화하여 저장)
     * - memberId: 회원 ID
     * - coupleId: nullable
     * - role: "A" / "B" / null
     * - accessExpMs: access token 만료 시각 (밀리초 epoch)
     */
    public void registerAccessToken(String accessToken, Long memberId, Long coupleId, String role, long accessExpMs) {
        String hash = TokenHashes.sha256B64(accessToken);
        String key = tokenKey(hash);

        long nowMs = System.currentTimeMillis();
        long ttlSec = Math.max(1, (accessExpMs - nowMs) / 1000L);
        ttlSec = Math.min(ttlSec, MAX_CACHE_TTL_SECONDS);

        try {
            ObjectNode node = om.createObjectNode();
            node.put("active", true);
            node.put("memberId", memberId);
            if (coupleId != null) node.put("coupleId", String.valueOf(coupleId));
            else node.putNull("coupleId");
            if (role != null) node.put("role", role);
            else node.putNull("role");
            node.put("exp", accessExpMs / 1000L); // epoch seconds

            redis.opsForValue().set(key, node.toString(), Duration.ofSeconds(ttlSec));

            // add token hash to user's set and keep a TTL on the set at least as long as token ttl
            String setKey = userTokensKey(memberId);
            redis.opsForSet().add(setKey, hash);
            redis.expire(setKey, Duration.ofSeconds(ttlSec));
        } catch (Exception e) {
            log.error("Failed to register access token in redis for memberId={}: {}", memberId, e.getMessage());
            throw new IllegalStateException(e);
        }
    }

    /**
     * Blacklist a token given its hash (sha256B64). TTL should be remaining lifetime.
     * Also deletes token:{hash} to force cache miss.
     */
    public void blacklistTokenByHash(String hash, long remainingSeconds) {
        String blKey = blacklistKey(hash);
        try {
            long ttl = Math.max(1, remainingSeconds);
            redis.opsForValue().set(blKey, "1", Duration.ofSeconds(ttl));
            // optionally delete token entry to force miss immediately
            redis.delete(tokenKey(hash));
        } catch (Exception e) {
            log.warn("Failed to blacklist token {}: {}", hash, e.getMessage());
        }
    }

    /**
     * Blacklist an access token by its raw value (convenience).
     */
    public void blacklistAccessToken(String accessToken, long remainingSeconds) {
        String hash = TokenHashes.sha256B64(accessToken);
        blacklistTokenByHash(hash, remainingSeconds);
    }

    /**
     * Revoke all access tokens for a member:
     * - iterate user_tokens:{memberId} set
     * - for each hash: if token:{hash} exists parse exp -> set blacklist:{hash} TTL accordingly; delete token:{hash}
     * - delete the user_tokens set
     */
    public void revokeAllAccessTokensForMember(Long memberId) {
        String setKey = userTokensKey(memberId);
        try {
            Set<String> hashes = redis.opsForSet().members(setKey);
            if (hashes == null || hashes.isEmpty()) {
                // nothing to revoke
                return;
            }
            for (String hash : hashes) {
                String tkKey = tokenKey(hash);
                String json = redis.opsForValue().get(tkKey);
                long remaining = 60; // default fallback
                if (json != null) {
                    try {
                        JsonNode node = om.readTree(json);
                        long exp = node.path("exp").asLong(Instant.now().getEpochSecond() + 60);
                        long now = Instant.now().getEpochSecond();
                        remaining = Math.max(1, exp - now);
                    } catch (Exception ignore) {}
                }
                // set blacklist + remove token key
                redis.opsForValue().set(blacklistKey(hash), "1", Duration.ofSeconds(Math.max(1, remaining)));
                redis.delete(tkKey);
            }
            // finally remove the set
            redis.delete(setKey);
        } catch (Exception e) {
            log.error("Failed to revoke all access tokens for member {}: {}", memberId, e.getMessage());
            // don't rethrow to avoid cascade failure; caller can handle logging/alerts
        }
    }

    /**
     * Update token JSON fields (coupleId / role) for a specific raw access token.
     * - used after user registers and coupleId becomes available.
     * - returns true if updated, false if token entry not found.
     */
//    public boolean updateTokenFields(String accessToken, Long coupleId, String role) {
//        String hash = TokenHashes.sha256B64(accessToken);
//        String key = tokenKey(hash);
//        try {
//            String json = redis.opsForValue().get(key);
//            if (json == null) return false;
//            ObjectNode node = (ObjectNode) om.readTree(json);
//            if (coupleId != null) node.put("coupleId", String.valueOf(coupleId));
//            else node.putNull("coupleId");
//            if (role != null) node.put("role", role);
//            else node.putNull("role");
//
//            // preserve existing ttl
//            Long ttlSec = redis.getExpire(key);
//            if (ttlSec == null || ttlSec <= 0) ttlSec = MAX_CACHE_TTL_SECONDS;
//            redis.opsForValue().set(key, node.toString(), Duration.ofSeconds(ttlSec));
//            return true;
//        } catch (Exception e) {
//            log.warn("Failed to update token fields for hash {}: {}", hash, e.getMessage());
//            return false;
//        }
//    }


    public int updateAllTokensForMember(Long memberId, Long coupleId, String role) {
        String setKey = userTokensKey(memberId);              // user_tokens:{memberId}
        try {
            Set<String> hashes = redis.opsForSet().members(setKey);
            if (hashes == null || hashes.isEmpty()) return 0;

            int updated = 0;
            for (String hash : hashes) {
                String tkKey = tokenKey(hash);                // token:{hash}
                String json = redis.opsForValue().get(tkKey);
                if (json == null) continue;

                ObjectNode node = (ObjectNode) om.readTree(json);

                // 전달된 값으로 upsert
                if (coupleId != null) node.put("coupleId", String.valueOf(coupleId));
                else node.putNull("coupleId");

                if (role != null) node.put("role", role);
                else node.putNull("role");

                Long ttlSec = redis.getExpire(tkKey);
                if (ttlSec == null || ttlSec <= 0) ttlSec = MAX_CACHE_TTL_SECONDS;

                redis.opsForValue().set(tkKey, node.toString(), Duration.ofSeconds(ttlSec));
                updated++;
            }
            return updated;
        } catch (Exception e) {
            log.warn("Failed to update tokens for memberId={}: {}", memberId, e.getMessage());
            return 0;
        }
    }

    /**
     * Optional: helper to compute remaining seconds for a given token hash (returns >=1)
     */
    public long computeRemainingSecondsForTokenHash(String hash) {
        String json = redis.opsForValue().get(tokenKey(hash));
        if (json == null) return 60;
        try {
            JsonNode node = om.readTree(json);
            long exp = node.path("exp").asLong(Instant.now().getEpochSecond() + 60);
            long now = Instant.now().getEpochSecond();
            return Math.max(1, exp - now);
        } catch (Exception e) {
            return 60;
        }
    }
}