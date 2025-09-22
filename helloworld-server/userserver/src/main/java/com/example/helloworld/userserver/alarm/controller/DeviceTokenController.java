package com.example.helloworld.userserver.alarm.controller;

import com.example.helloworld.userserver.alarm.dto.FcmTokenResponse;
import com.example.helloworld.userserver.alarm.entity.DeviceToken;
import com.example.helloworld.userserver.alarm.persistence.DeviceTokenRepository;
import com.example.helloworld.userserver.alarm.security.AuthUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fcm")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final AuthUserResolver auth;
    private final DeviceTokenRepository repo;

    public record RegisterReq(String token, String platform) {}
    public record UnregisterReq(String token) {}

    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestHeader("Authorization") String authz,
                                         @RequestBody RegisterReq body) {
        Long uid = auth.requireUserId(authz);
        repo.upsert(uid, body.token(), body.platform());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/unregister")
    public ResponseEntity<Void> unregister(@RequestHeader("Authorization") String authz,
                                           @RequestBody UnregisterReq body) {
        Long uid = auth.requireUserId(authz);
        repo.deactivate(uid, body.token());
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
