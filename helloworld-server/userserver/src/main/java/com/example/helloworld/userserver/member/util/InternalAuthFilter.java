package com.example.helloworld.userserver.member.util;

import com.example.helloworld.userserver.auth.jwt.JwtProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.util.StringUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Component
@Order(0)
public class InternalAuthFilter extends OncePerRequestFilter {
    private final JwtProvider jwt;
    private final SecretKeySpec hmacKey;
    private final long tsWindowSec;

    public InternalAuthFilter(
            JwtProvider jwt,
            @Value("${gateway.hmac-secret}") String secret,
            @Value("${security.internal.ts-window:60}") long tsWindowSec
    ) {
        this.jwt = jwt;
        this.hmacKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.tsWindowSec = tsWindowSec;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        // 1) 게이트웨이 내부 헤더 우선
        String uid = req.getHeader("X-Internal-User-Id");
        String ts  = req.getHeader("X-Internal-Ts");
        String sig = req.getHeader("X-Internal-Sig");

        String coupleIdHdr = req.getHeader("X-Internal-Couple-Id");
        String roleHdr     = req.getHeader("X-Internal-Role");
        String tokenHash   = req.getHeader("X-Internal-Token-Hash");


        if (hasText(uid) && hasText(ts) && hasText(sig) && verify(uid, ts, sig)) {
            setAuth(uid);
        } else {
            // 2) 없으면 Bearer 토큰 폴백 (로컬/직접 호출용)
            String authz = req.getHeader("Authorization");
            if (hasText(authz) && authz.toLowerCase().startsWith("bearer ")) {
                try {
                    Long memberId = jwt.parseAccessSubject(authz.substring(7).trim());
                    setAuth(String.valueOf(memberId));
                } catch (Exception ignore) {
                    // 여기서 401 던지지 않음 — 보안 설정이 판단
                }
            }
        }

        chain.doFilter(req, res);
    }

    private boolean verify(String uid, String ts, String sigB64) {
        long now = Instant.now().getEpochSecond();
        long t;
        try { t = Long.parseLong(ts); } catch (Exception e) { return false; }
        if (Math.abs(now - t) > tsWindowSec) return false;

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            byte[] expected = mac.doFinal((uid + "|" + ts).getBytes(StandardCharsets.UTF_8));
            byte[] actual   = Base64.getDecoder().decode(sigB64);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private void setAuth(String uid) {
        var principal = new org.springframework.security.core.userdetails.User(
                uid, "", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static boolean hasText(String s){ return s != null && !s.isBlank(); }
}


