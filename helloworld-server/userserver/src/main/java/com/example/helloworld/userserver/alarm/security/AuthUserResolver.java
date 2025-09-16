package com.example.helloworld.userserver.alarm.security;

import com.example.helloworld.userserver.auth.jwt.JwtProvider;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Authorization: Bearer ... 에서 userId를 추출하는 유틸.
 * 실제 구현: JWT 파싱하여 subject를 Long으로 변환.
 */
@Component
@RequiredArgsConstructor
public class AuthUserResolver {

    private final JwtProvider jwtProvider; // ★ 인스턴스 주입

    public Long requireUserId(String authz) {
        if (authz == null || !authz.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing Bearer token");
        }
        String at = authz.substring(7).trim();
        try {
            Long uid = jwtProvider.parseAccessSubject(at); // ★ 인스턴스 메서드 호출
            if (uid == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No sub");
            return uid;
        } catch (ExpiredJwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Expired");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid");
        }
    }
}