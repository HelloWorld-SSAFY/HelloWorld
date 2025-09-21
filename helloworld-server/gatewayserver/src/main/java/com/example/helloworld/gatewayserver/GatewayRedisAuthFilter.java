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
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Component
public class GatewayRedisAuthFilter implements GlobalFilter {

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper om = new ObjectMapper();
    private final SecretKeySpec hmacKey;
    private final AntPathMatcher matcher = new AntPathMatcher();
    private final List<String> skipPaths;

    public GatewayRedisAuthFilter(
            ReactiveStringRedisTemplate redis,
            @Value("${gateway.hmac-secret}") String secret,
            @Value("${gateway.auth.skip-paths}") List<String> skipPaths) {
        this.redis = redis;
        this.hmacKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.skipPaths = skipPaths;
    }

    @Override
    public @NonNull Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        for (String p : skipPaths) {
            if (matcher.match(p, path)) return chain.filter(exchange);
        }

        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) return unauthorized(exchange);

        String token = auth.substring(7);
        String hash = sha256B64(token);
        String blacklistKey = "blacklist:" + hash;
        String tokenKey     = "token:" + hash;

        return redis.hasKey(blacklistKey)
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) return unauthorized(exchange);
                    return redis.opsForValue().get(tokenKey)
                            .flatMap(json -> {
                                if (json == null) return unauthorized(exchange); // 일부 드라이버는 null emit
                                try {
                                    JsonNode node = om.readTree(json);
                                    boolean active = node.path("active").asBoolean(false);
                                    long exp = node.path("exp").asLong(0L);
                                    long now = Instant.now().getEpochSecond();
                                    if (!active || (exp > 0 && now >= exp)) return unauthorized(exchange);

                                    String memberId = node.path("memberId").asText(null);
                                    String coupleId = node.path("coupleId").isNull() ? null : node.path("coupleId").asText(null);
                                    String role     = node.path("role").isNull()     ? null : node.path("role").asText(null);

                                    String ts  = String.valueOf(now);
                                    String sig = hmacBase64(memberId + "|" + ts);

                                    var mutated = exchange.getRequest().mutate();
                                    mutated.headers(h -> {
                                        h.remove("Authorization");
                                        h.add("X-Internal-User-Id", memberId);
                                        if (coupleId != null) h.add("X-Internal-Couple-Id", coupleId);
                                        if (role != null)     h.add("X-Internal-Role", role);
                                        h.add("X-Internal-Ts",  ts);
                                        h.add("X-Internal-Sig", sig);
                                    });
                                    return chain.filter(exchange.mutate().request(mutated.build()).build());
                                } catch (Exception e) {
                                    exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                                    return exchange.getResponse().setComplete();
                                }
                            })
                            // 드라이버가 empty complete를 줄 수도 있으니 안전망
                            .switchIfEmpty(unauthorized(exchange));
                });
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }


    private String sha256B64(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(md.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private String hmacBase64(String msg) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return Base64.getEncoder().encodeToString(mac.doFinal(msg.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}

