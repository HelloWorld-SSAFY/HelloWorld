package com.example.helloworld.gatewayserver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class GatewayRedisAuthFilter implements GlobalFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayRedisAuthFilter.class);

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper om = new ObjectMapper();
    private final SecretKeySpec hmacKey;
    private final AntPathMatcher matcher = new AntPathMatcher();
    private final List<String> skipPaths;
    private final Duration redisTimeout;

    public GatewayRedisAuthFilter(
            ReactiveStringRedisTemplate redis,
            @Value("${gateway.hmac-secret}") String secret,
            @Value("${gateway.auth.skip-paths}") List<String> skipPaths,
            @Value("${gateway.redis.timeout:2}") int timeoutSeconds) {
        this.redis = redis;
        this.hmacKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.skipPaths = skipPaths;
        this.redisTimeout = Duration.ofSeconds(timeoutSeconds);
    }

    @Override
    public @NonNull Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Skip paths that don't require authentication
        for (String p : skipPaths) {
            if (matcher.match(p, path)) {
                return chain.filter(exchange);
            }
        }

        // Extract Bearer token
        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        String token = auth.substring(7);
        String hash = sha256B64(token);
        String blacklistKey = "blacklist:" + hash;
        String tokenKey = "token:" + hash;

        // Check blacklist with proper error handling
        return checkBlacklist(blacklistKey)
                .flatMap(blacklisted -> {
                    if (blacklisted) {
                        log.debug("Token {} is blacklisted", hash);
                        return unauthorized(exchange);
                    }
                    return authenticateToken(exchange, chain, tokenKey, hash);
                })
                .onErrorResume(e -> {
                    log.error("Authentication filter error: {}", e.getMessage());
                    if (e instanceof RedisException) {
                        return serviceUnavailable(exchange);
                    }
                    return internalError(exchange);
                });
    }

    private Mono<Boolean> checkBlacklist(String blacklistKey) {
        return redis.hasKey(blacklistKey)
                .timeout(redisTimeout)
                .onErrorResume(e -> {
                    log.error("Redis error checking blacklist: {}", e.getMessage());
                    // Policy decision: fail-open (false) or fail-closed (throw exception)
                    // Option 1: Fail-open (continue if Redis is down)
                    // return Mono.just(false);

                    // Option 2: Fail-closed (reject if Redis is down) - more secure
                    return Mono.error(new RedisException("Redis unavailable"));
                });
    }

    private Mono<Void> authenticateToken(ServerWebExchange exchange, GatewayFilterChain chain,
                                         String tokenKey, String hash) {
        return redis.opsForValue().get(tokenKey)
                .timeout(redisTimeout)
                .onErrorResume(e -> {
                    log.error("Redis error fetching token: {}", e.getMessage());
                    return Mono.error(new RedisException("Redis unavailable"));
                })
                .flatMap(json -> processTokenData(exchange, chain, json, hash))
                .switchIfEmpty(unauthorized(exchange));
    }

    // Custom exception for Redis issues
    private static class RedisException extends RuntimeException {
        public RedisException(String message) {
            super(message);
        }
    }

    private Mono<Void> processTokenData(ServerWebExchange exchange, GatewayFilterChain chain,
                                        String json, String hash) {
        if (json == null) {
            return unauthorized(exchange);
        }

        try {
            JsonNode node = om.readTree(json);

            // Extract token data
            boolean active = node.path("active").asBoolean(false);
            long exp = node.path("exp").asLong(0L);
            long now = Instant.now().getEpochSecond();

            // Validate token status
            if (!active || (exp > 0 && now >= exp)) {
                log.debug("Token inactive or expired: active={}, exp={}, now={}", active, exp, now);
                return unauthorized(exchange);
            }

            // Extract user data
            String memberId = node.path("memberId").asText(null);
            String coupleId = node.path("coupleId").isNull() ? null : node.path("coupleId").asText(null);
            String role = node.path("role").isNull() ? null : node.path("role").asText(null);

            // Validate required fields
            if (memberId == null || memberId.isBlank()) {
                log.error("Missing memberId in token data");
                return unauthorized(exchange);
            }

            // Create timestamp and signature
            String ts = String.valueOf(now);
            String payload = createPayload(memberId, coupleId, role, ts);
            String sig = hmacBase64(payload);

            // Add internal headers
            var mutated = exchange.getRequest().mutate();
            mutated.headers(h -> {
                // Required headers
                h.add("X-Internal-User-Id", memberId);
                h.add("X-Internal-Ts", ts);
                h.add("X-Internal-Sig", sig);
                h.add("X-Internal-Token-Hash", hash);

                // Optional headers
                if (coupleId != null && !coupleId.isBlank()) {
                    h.add("X-Internal-Couple-Id", coupleId);
                }
                if (role != null && !role.isBlank()) {
                    h.add("X-Internal-Role", role);
                }

                // Remove original Authorization header to prevent bypass
                h.remove("Authorization");
            });

            // Audit logging for healthcare compliance
            auditLog(memberId, coupleId, exchange.getRequest().getPath().value(),
                    exchange.getRequest().getMethod().toString());

            return chain.filter(exchange.mutate().request(mutated.build()).build());

        } catch (Exception e) {
            log.error("Error processing token data: {}", e.getMessage(), e);
            return internalError(exchange);
        }
    }

    private String createPayload(String memberId, String coupleId, String role, String ts) {
        // Create consistent payload for HMAC signature
        return String.format("%s|%s|%s|%s",
                memberId,
                coupleId != null ? coupleId : "",
                role != null ? role : "",
                ts
        );
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        String body = "{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing authentication\"}";
        var buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private Mono<Void> serviceUnavailable(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        String body = "{\"error\":\"Service Unavailable\",\"message\":\"Authentication service temporarily unavailable\"}";
        var buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private Mono<Void> internalError(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        String body = "{\"error\":\"Internal Server Error\",\"message\":\"An error occurred processing the request\"}";
        var buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private String sha256B64(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(
                    md.digest(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }

    private String hmacBase64(String msg) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return Base64.getEncoder().encodeToString(
                    mac.doFinal(msg.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 generation failed", e);
        }
    }

    private void auditLog(String memberId, String coupleId, String path, String method) {
        // Healthcare compliance audit logging (HIPAA/KDPA)
        log.info("AUDIT_ACCESS: timestamp={}, memberId={}, coupleId={}, path={}, method={}",
                Instant.now(), memberId, coupleId != null ? coupleId : "N/A", path, method);
    }
}