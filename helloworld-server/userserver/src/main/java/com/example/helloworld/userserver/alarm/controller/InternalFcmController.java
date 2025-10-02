package com.example.helloworld.userserver.alarm.controller;

import com.example.helloworld.userserver.alarm.persistence.DeviceTokenRepository;
import com.example.helloworld.userserver.member.persistence.CoupleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

// user-server
@RestController
@RequestMapping("/api/internal/fcm")
@RequiredArgsConstructor
public class InternalFcmController {

    private final DeviceTokenRepository repo;

    public record LatestTwoResponse(Long userId, String androidToken, String watchToken) {}
    public record FcmTokenResponse(Long userId, String platform, String token) {}

    // 별칭 매핑 (조회시에만 사용)
    private static List<String> aliases(String key) {
        if (key == null) return List.of();
        String k = key.trim().toUpperCase();
        return switch (k) {
            case "ANDROID", "MOBILE" -> List.of("ANDROID", "MOBILE");
            case "WATCH", "WEAR_OS", "WEAROS" -> List.of("WATCH", "WEAR_OS", "WEAROS");
            // 필요시 IOS 등 추가
            case "IOS", "IPHONE" -> List.of("IOS", "IPHONE");
            default -> List.of(k); // 알 수 없는 값은 그대로 1개 리스트
        };
    }

    @GetMapping("/users/{userId}/latest-two")
    public ResponseEntity<LatestTwoResponse> latestTwo(@PathVariable Long userId) {
        var a = repo.findFirstByUserIdAndPlatformInAndIsActiveTrueOrderByLastSeenAtDescCreatedAtDesc(
                userId, aliases("ANDROID")).orElse(null);
        var w = repo.findFirstByUserIdAndPlatformInAndIsActiveTrueOrderByLastSeenAtDescCreatedAtDesc(
                userId, aliases("WATCH")).orElse(null);

        if (a == null && w == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(new LatestTwoResponse(
                userId,
                a != null ? a.getToken() : null,
                w != null ? w.getToken() : null
        ));
    }

    @GetMapping("/users/{userId}/latest")
    public ResponseEntity<FcmTokenResponse> latestByPlatform(@PathVariable Long userId,
                                                             @RequestParam String platform) {
        var t = repo.findFirstByUserIdAndPlatformInAndIsActiveTrueOrderByLastSeenAtDescCreatedAtDesc(
                userId, aliases(platform));
        return t.map(dt -> ResponseEntity.ok(new FcmTokenResponse(userId, platform.toUpperCase(), dt.getToken())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}