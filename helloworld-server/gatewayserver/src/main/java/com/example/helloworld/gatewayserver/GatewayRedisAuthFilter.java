package com.example.helloworld.gatewayserver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.annotation.NonNull;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Order(-100)
@Component
public class GatewayRedisAuthFilter implements GlobalFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayRedisAuthFilter.class);

    private final org.springframework.data.redis.core.ReactiveStringRedisTemplate redis;
    private final ObjectMapper om = new ObjectMapper();
    private final SecretKeySpec hmacKey;
    private final AntPathMatcher matcher = new AntPathMatcher();
    private final List<String> skipPaths;
    private final Duration redisTimeout;

    public GatewayRedisAuthFilter(
            org.springframework.data.redis.core.ReactiveStringRedisTemplate redis,
            @Value("${gateway.hmac-secret}") String secret,
            @Value("${gateway.auth.skip-paths}") List<String> skipPaths,
            @Value("${gateway.redis.timeout:2}") int timeoutSeconds
    ) {
        this.redis = redis;
        this.hmacKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.skipPaths = skipPaths;
        this.redisTimeout = Duration.ofSeconds(timeoutSeconds);
        log.info("GatewayRedisAuthFilter init: timeout={}s, skipPaths={}", timeoutSeconds, skipPaths);
    }

    @Override
    public @NonNull Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 0) CORS preflight 통과
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        // 0-1) 요청 추적용 RID 생성 & 헤더 주입 (exchange 재할당 금지!)
        final String rid = UUID.randomUUID().toString().substring(0, 8);
        final ServerWebExchange exWithRid = exchange.mutate()
                .request(r -> r.headers(h -> h.add("X-Request-Id", rid)))
                .build();

        final String path = exWithRid.getRequest().getURI().getPath();

        // 1) 스킵 경로
        for (String p : skipPaths) {
            if (matcher.match(p, path)) {
                log.info("RID={} SKIP path={} pattern={}", rid, path, p);
                return chain.filter(exWithRid);
            }
        }

        // 2) Authorization 검사
        final String auth = exWithRid.getRequest().getHeaders().getFirst("Authorization");
        log.info("RID={} Authorization header: {}", rid, auth != null ? "present" : "missing");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exWithRid, rid, "no_or_invalid_authorization_header");
        }

        final String token = auth.substring(7);
        final String hash = sha256B64(token);
        final String blacklistKey = "blacklist:" + hash;
        final String tokenKey = "token:" + hash;

        log.debug("RID={} checking token hash: {}", rid, hash);

        return redis.hasKey(blacklistKey)
                .timeout(redisTimeout)
                .onErrorResume(e -> {
                    log.error("RID={} redis error checking blacklist: {}", rid, e.toString());
                    return Mono.just(false); // 진단 단계: fail-open
                })
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        return unauthorized(exWithRid, rid, "blacklisted_token");
                    }
                    return redis.opsForValue().get(tokenKey)
                            .timeout(redisTimeout)
                            .doOnNext(json -> {
                                if (json != null) log.debug("RID={} redis token data: {}", rid, json);
                                else log.warn("RID={} no token data in redis for hash={}", rid, hash);
                            })
                            .onErrorResume(e -> {
                                log.error("RID={} redis error fetching token: {}", rid, e.toString());
                                return Mono.empty();
                            })
                            .flatMap(json -> processTokenData(exWithRid, chain, json, hash, rid))
                            .switchIfEmpty(unauthorized(exWithRid, rid, "no_token_in_redis"));
                });
    }

    private Mono<Void> processTokenData(ServerWebExchange exchange, GatewayFilterChain chain,
                                        String json, String hash, String rid) {
        if (json == null) return unauthorized(exchange, rid, "null_token_json");

        try {
            JsonNode node = om.readTree(json);

            boolean active = node.path("active").asBoolean(false);
            long exp = node.path("exp").asLong(0L);
            long now = Instant.now().getEpochSecond();

            if (!active || (exp > 0 && now >= exp)) {
                return unauthorized(exchange, rid,
                        "inactive_or_expired:active=" + active + ",exp=" + exp + ",now=" + now);
            }

            String memberId = node.path("memberId").asText(null);
            String coupleId = node.path("coupleId").isNull() ? null : node.path("coupleId").asText(null);
            String role = node.path("role").isNull() ? null : node.path("role").asText(null);

            if (memberId == null || memberId.isBlank()) {
                return unauthorized(exchange, rid, "missing_memberId_in_token");
            }

            String ts = String.valueOf(now);
            String payload = createPayload(memberId, coupleId, role, ts);
            String sig = hmacBase64(payload);

            var mutated = exchange.getRequest().mutate();
            mutated.headers(h -> {
                h.add("X-Internal-User-Id", memberId);
                h.add("X-Internal-Ts", ts);
                h.add("X-Internal-Sig", sig);
                h.add("X-Internal-Token-Hash", hash);
                if (coupleId != null && !coupleId.isBlank()) h.add("X-Internal-Couple-Id", coupleId);
                if (role != null && !role.isBlank()) h.add("X-Internal-Role", role);
                h.remove("Authorization");
            });

            // getMethodValue() 대체: null-safe name()
            String method = exchange.getRequest().getMethod() != null
                    ? exchange.getRequest().getMethod().name()
                    : "UNKNOWN";

            auditLog(memberId, coupleId, exchange.getRequest().getPath().value(), method);
            log.info("RID={} AUTH_OK uid={} coupleId={} path={}", rid, memberId, coupleId,
                    exchange.getRequest().getPath());

            return chain.filter(exchange.mutate().request(mutated.build()).build());

        } catch (Exception e) {
            log.error("RID={} error processing token data: {}", rid, e.toString(), e);
            return internalError(exchange, rid);
        }
    }

    private String createPayload(String memberId, String coupleId, String role, String ts) {
        return String.format("%s|%s|%s|%s",
                memberId,
                coupleId != null ? coupleId : "",
                role != null ? role : "",
                ts
        );
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String rid, String reason) {
        String path = exchange.getRequest().getPath().value();
        log.warn("RID={} UNAUTHORIZED path={} reason={}", rid, path, reason);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().set("Content-Type", "application/json");
        String body = "{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing authentication\"}";
        var buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private Mono<Void> internalError(ServerWebExchange exchange, String rid) {
        String path = exchange.getRequest().getPath().value();
        log.error("RID={} INTERNAL_ERROR path={}", rid, path);
        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        exchange.getResponse().getHeaders().set("Content-Type", "application/json");
        String body = "{\"error\":\"Internal Server Error\",\"message\":\"An error occurred processing the request\"}";
        var buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private String sha256B64(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(md.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }

    private String hmacBase64(String msg) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return Base64.getEncoder().encodeToString(mac.doFinal(msg.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 generation failed", e);
        }
    }

    private void auditLog(String memberId, String coupleId, String path, String method) {
        log.info("AUDIT_ACCESS: timestamp={}, memberId={}, coupleId={}, path={}, method={}",
                Instant.now(), memberId, coupleId != null ? coupleId : "N/A", path, method);
    }
}
