package com.example.helloworld.healthserver.alarm.controller;

import com.example.helloworld.healthserver.alarm.dto.AiResponse;
import com.example.helloworld.healthserver.dto.HealthDtos;
import com.example.helloworld.healthserver.service.HealthDataService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WearableController {

    private final HealthDataService service;

    @PostMapping("/wearable")
    @Operation(summary = "헬스데이터 생성")
    public ResponseEntity<AiResponse> createAndEvaluate(
            @RequestHeader("X-Couple-Id") Long coupleId,
            @RequestBody HealthDtos.CreateRequest req
    ) {
        AiResponse ai = service.createAndEvaluate(coupleId, req);
        return ResponseEntity.ok(ai); // ★ AI 응답 그대로 반환
    }
}
