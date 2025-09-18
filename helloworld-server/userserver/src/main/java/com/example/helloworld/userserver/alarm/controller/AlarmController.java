package com.example.helloworld.userserver.alarm.controller;

import com.example.helloworld.userserver.alarm.dto.Notification;
import com.example.helloworld.userserver.alarm.presentation.AlarmCreateRequest;
import com.example.helloworld.userserver.alarm.presentation.AlarmQueuedResponse;
import com.example.helloworld.userserver.alarm.security.AuthUserResolver;
import com.example.helloworld.userserver.alarm.service.AlarmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alert")
@RequiredArgsConstructor
public class AlarmController {

    private final AlarmService alertService;
    private final AuthUserResolver auth;

    @PostMapping
    public ResponseEntity<AlarmQueuedResponse> sendAlert(
            @RequestHeader("Authorization") String authz,
            @Valid @RequestBody AlarmCreateRequest req) {

        System.out.println("DEBUG /api/alert hit, authz=" + authz);   // A
        Long senderUserId = auth.requireUserId(authz);
        System.out.println("DEBUG resolved userId=" + senderUserId);   // B
        Notification noti = alertService.queueAndSendToCouple(senderUserId, req);
        return ResponseEntity.accepted()
                .body(new AlarmQueuedResponse(String.valueOf(noti.getAlarmId()), "QUEUED"));
    }
}
