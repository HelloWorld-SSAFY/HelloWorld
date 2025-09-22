package com.example.helloworld.healthserver.alarm.controller;

import com.example.helloworld.healthserver.alarm.repository.DeviceTokenRepository;
import com.example.helloworld.healthserver.alarm.security.AuthUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
