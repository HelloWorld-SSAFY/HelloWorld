package com.example.helloworld.userserver.member.util;

import com.example.helloworld.userserver.auth.jwt.JwtProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * 내부 게이트웨이 서명 헤더(X-Internal-*)를 우선 신뢰하고,
 * 없거나 검증 실패 시 Bearer JWT로 폴백하여 SecurityContext에 InternalPrincipal을 주입한다.
 *
 * 우선순위: @Order(0)로 가장 앞단에서 동작하도록 설정.
 */
@Component
@Order(0)
public class InternalAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalAuthFilter.class);

    private final JwtProvider jwt;
    private final SecretKeySpec hmacKey;
    private final long tsWindowSec;
    private final boolean allowBearerFallback;

    public InternalAuthFilter(
            JwtProvider jwt,
            @Value("${gateway.hmac-secret}") String secret,
            @Value("${security.internal.ts-window:30}") long tsWindowSec,
            @Value("${security.internal.allow-bearer-fallback:false}") boolean allowBearerFallback
    ) {
        this.jwt = jwt;
        this.hmacKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.tsWindowSec = tsWindowSec;
        this.allowBearerFallback = allowBearerFallback;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        // 1) 내부 게이트웨이 서명 헤더 기반 인증
        boolean authenticated = setAuthFromInternalHeaders(req);

        // 2) 폴백: Bearer JWT (설정에 따라)
        if (!authenticated && allowBearerFallback && isInternalNetwork(req)) {
            setAuthFromBearer(req);
        }

        chain.doFilter(req, res);
    }

    /**
     * X-Internal-* 헤더를 검증하고 유효하면 InternalPrincipal을 SecurityContext에 설정.
     * @return 인증 성공 여부
     */
    private boolean setAuthFromInternalHeaders(HttpServletRequest req) {
        String uid = req.getHeader("X-Internal-User-Id");
        String ts = req.getHeader("X-Internal-Ts");
        String sig = req.getHeader("X-Internal-Sig");
        String coupleId = req.getHeader("X-Internal-Couple-Id");
        String role = req.getHeader("X-Internal-Role");
        String tokenHash = req.getHeader("X-Internal-Token-Hash");

        // 필수 헤더 확인
        if (!hasText(uid) || !hasText(ts) || !hasText(sig)) {
            return false;
        }

        // 서명 검증 (coupleId와 role도 포함)
        if (!verify(uid, coupleId, role, ts, sig)) {
            log.warn("Invalid signature for user: {}", uid);
            return false;
        }

        try {
            Long memberId = Long.valueOf(uid);
            Long coupleIdVal = hasText(coupleId) ? Long.valueOf(coupleId) : null;
            String roleVal = hasText(role) ? role : "ROLE_USER";

            InternalPrincipal principal = new InternalPrincipal(memberId, coupleIdVal, roleVal, tokenHash);
            setAuth(principal);

            // Audit logging for healthcare compliance
            auditLog(principal, req);

            return true;
        } catch (NumberFormatException e) {
            log.error("Invalid number format in headers: uid={}, coupleId={}", uid, coupleId);
            return false;
        }
    }

    /**
     * Authorization: Bearer <jwt>를 파싱해 subject(memberId)를 얻고 SecurityContext에 설정.
     * 내부 네트워크에서만 허용
     */
    private void setAuthFromBearer(HttpServletRequest req) {
        String authz = req.getHeader("Authorization");
        if (hasText(authz) && authz.toLowerCase().startsWith("bearer ")) {
            try {
                String token = authz.substring(7).trim();
                Long memberId = jwt.parseAccessSubject(token);
                if (memberId != null) {
                    InternalPrincipal principal = new InternalPrincipal(memberId, null, "ROLE_USER", null);
                    setAuth(principal);
                    log.debug("Authenticated via Bearer token for user: {}", memberId);
                }
            } catch (Exception e) {
                log.debug("Bearer token authentication failed: {}", e.getMessage());
            }
        }
    }

    /**
     * SecurityContext에 InternalPrincipal을 principal로 넣는다.
     */
    private void setAuth(InternalPrincipal principal) {
        String role = principal.role() != null ? principal.role() : "ROLE_USER";
        var auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(role))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /**
     * 게이트웨이 서명 검증: HMAC-SHA256(payload), Base64 인코딩 비교 + 타임스탬프 윈도우 체크.
     * 게이트웨이와 동일한 페이로드 구조 사용
     */
    private boolean verify(String uid, String coupleId, String role, String ts, String sigB64) {
        // 타임스탬프 검증
        long now = Instant.now().getEpochSecond();
        long timestamp;
        try {
            timestamp = Long.parseLong(ts);
        } catch (NumberFormatException e) {
            log.error("Invalid timestamp format: {}", ts);
            return false;
        }

        // 타임스탬프 윈도우 체크
        if (Math.abs(now - timestamp) > tsWindowSec) {
            log.debug("Timestamp outside window: now={}, ts={}, window={}s", now, timestamp, tsWindowSec);
            return false;
        }

        try {
            // 게이트웨이와 동일한 페이로드 생성
            String payload = String.format("%s|%s|%s|%s",
                    uid,
                    coupleId != null ? coupleId : "",
                    role != null ? role : "",
                    ts
            );

            // HMAC 계산
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            byte[] expected = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            // Base64 디코딩 및 비교
            byte[] actual = Base64.getDecoder().decode(sigB64);
            boolean valid = MessageDigest.isEqual(expected, actual);

            if (!valid) {
                log.debug("Signature mismatch for payload: {}", payload);
            }

            return valid;
        } catch (Exception e) {
            log.error("Error verifying signature: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 내부 네트워크 확인 (Kubernetes 클러스터 내부)
     */
    private boolean isInternalNetwork(HttpServletRequest req) {
        String remoteAddr = req.getRemoteAddr();
        return remoteAddr != null && (
                remoteAddr.startsWith("10.") ||      // Kubernetes pod network
                        remoteAddr.startsWith("172.") ||     // Docker network
                        remoteAddr.startsWith("192.168.") || // Private network
                        remoteAddr.equals("127.0.0.1") ||    // Localhost
                        remoteAddr.equals("::1")             // IPv6 localhost
        );
    }

    /**
     * 감사 로그 (헬스케어 컴플라이언스)
     */
    private void auditLog(InternalPrincipal principal, HttpServletRequest req) {
        log.info("AUDIT_ACCESS: timestamp={}, memberId={}, coupleId={}, role={}, path={}, method={}, ip={}",
                Instant.now(),
                principal.memberId(),
                principal.coupleId() != null ? principal.coupleId() : "N/A",
                principal.role(),
                req.getRequestURI(),
                req.getMethod(),
                req.getRemoteAddr()
        );
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}