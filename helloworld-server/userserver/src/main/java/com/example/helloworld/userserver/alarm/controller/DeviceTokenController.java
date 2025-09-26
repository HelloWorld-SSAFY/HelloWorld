package com.example.helloworld.userserver.alarm.controller;

import com.example.helloworld.userserver.alarm.dto.FcmTokenResponse;
import com.example.helloworld.userserver.alarm.entity.DeviceToken;
import com.example.helloworld.userserver.alarm.persistence.DeviceTokenRepository;
import com.example.helloworld.userserver.member.util.InternalPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/fcm")
@RequiredArgsConstructor
public class DeviceTokenController {


    private final DeviceTokenRepository repo;

    public record RegisterReq(String token,String platform) {}
    public record UnregisterReq(String token) {}

    // 인증된 사용자 정보가 없을 경우 예외를 던지는 헬퍼 메소드
    private InternalPrincipal requireAuth(InternalPrincipal principal) {
        if (principal == null || principal.memberId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(
            @AuthenticationPrincipal InternalPrincipal principal, // 1. @AuthenticationPrincipal 사용
            @RequestBody RegisterReq body) {
        var auth = requireAuth(principal);
        String pf = normalize(body.platform());
        repo.upsert(auth.memberId(), body.token(),pf); // 2. principal에서 사용자 ID 사용
        return ResponseEntity.noContent().build();
    }

    private String normalize(String p) {
        if (p == null) return "ANDROID"; // 기본값 혹은 400으로 처리해도 됨
        return switch (p.trim().toUpperCase()) {
            case "MOBILE", "ANDROID" -> "ANDROID";
            case "WATCH", "WEAROS", "WEAR_OS" -> "WATCH";
            default -> p.trim().toUpperCase();
        };
    }

    @PostMapping("/unregister")
    public ResponseEntity<Void> unregister(
            @AuthenticationPrincipal InternalPrincipal principal, // 1. @AuthenticationPrincipal 사용
            @RequestBody UnregisterReq body) {
        var auth = requireAuth(principal);
        repo.deactivate(auth.memberId(), body.token()); // 2. principal에서 사용자 ID 사용
        return ResponseEntity.noContent().build();
    }
}
