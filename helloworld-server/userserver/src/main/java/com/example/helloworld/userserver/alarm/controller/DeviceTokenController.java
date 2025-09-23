package com.example.helloworld.userserver.alarm.controller;

import com.example.helloworld.userserver.alarm.dto.FcmTokenResponse;
import com.example.helloworld.userserver.alarm.entity.DeviceToken;
import com.example.helloworld.userserver.alarm.persistence.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fcm")
@RequiredArgsConstructor
public class DeviceTokenController {


    private final DeviceTokenRepository repo;

    public record RegisterReq(String token) {}
    public record UnregisterReq(String token) {}

    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestHeader("X-Member-Id") Long memberId,
                                         @RequestBody RegisterReq body) {
        // 2. 전달받은 memberId를 직접 사용합니다.
        repo.upsert(memberId, body.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/unregister")
    // 1. `@RequestHeader("Authorization")` 대신 `@RequestHeader("X-Member-Id")`를 받습니다.
    public ResponseEntity<Void> unregister(@RequestHeader("X-Member-Id") Long memberId,
                                           @RequestBody UnregisterReq body) {
        // 2. 전달받은 memberId를 직접 사용합니다.
        repo.deactivate(memberId, body.token());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}/fcm-tokens")
    public ResponseEntity<FcmTokenResponse> getFcmToken(@PathVariable Long userId) {
        var opt = repo.findFirstByUserIdAndIsActiveTrueOrderByLastSeenAtDescCreatedAtDesc(userId);
        if (opt.isEmpty()) {
            return ResponseEntity.noContent().build(); // 204: 활성 토큰 없음
        }
        return ResponseEntity.ok(
                FcmTokenResponse.builder()
                        .userId(userId)
                        .token(opt.get().getToken())
                        .build()
        );
    }

}
