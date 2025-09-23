package com.example.helloworld.healthserver.controller;

import com.example.helloworld.healthserver.client.AiServerClient;
import com.example.helloworld.healthserver.config.UserPrincipal;
import com.example.helloworld.healthserver.dto.HealthDtos;
import com.example.helloworld.healthserver.service.HealthDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@Tag(name = "Wearable Health", description = "웨어러블 건강 데이터 API (심박/스트레스/통계)")
@RestController
@RequestMapping("/api/wearable")
@RequiredArgsConstructor
public class WearableHealthController {

    private final HealthDataService healthService;

    // 2. Add a helper method to validate the authenticated principal
    private void requirePrincipal(UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User principal not found. Check gateway authentication headers.");
        }
    }

    @Operation(
            summary = "헬스데이터 생성 및 이상탐지",
            description = """
        웨어러블 기기에서 심박수, 스트레스 지수를 받아 저장하고 AI 서버로 전달하여 이상 징후를 감지합니다.
        - AI 서버의 탐지 결과를 그대로 반환합니다.
        - 이상 징후(restrict, emergency) 감지 시 파트너에게 FCM 알림을 보냅니다.
        """
    )
    @PostMapping
    public ResponseEntity<AiServerClient.AnomalyResponse> createAndCheck(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody HealthDtos.CreateRequest req
    ) {
        // 메소드 시작 시 인증 정보 유무를 확인합니다.
        requirePrincipal(user);
        // API는 커플 ID가 필수이므로, 커플 ID가 없는 경우 명시적인 에러를 반환합니다.
        if (user.getCoupleId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not associated with a couple.");
        }

        AiServerClient.AnomalyResponse response = healthService.createAndCheckHealthData(user, req);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "헬스데이터 단건 조회")
    @GetMapping("/{healthId}")
    public ResponseEntity<HealthDtos.GetResponse> getOne(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long healthId
    ) {
        return ResponseEntity.ok(healthService.getById(userPrincipal.getCoupleId(), healthId));
    }


//    @Operation(summary = "걸음수 누적 평균(전기간)", description = "구간: 00-12, 00-16. steps>0만 포함.")
//    @GetMapping("/overall-cumulative-avg")
//    public ResponseEntity<HealthDtos.StepResponse> overallCumulativeAvg(
//            @RequestParam Long coupleId
//    ) {
//        return ResponseEntity.ok(healthService.overallCumulativeAvg(coupleId));
//    }


    @Operation(summary = "전체 커플 일간 버킷 통계", description = "지정된 날짜(KST)의 모든 커플에 대한 심박수/스트레스 통계를 4시간 단위로 집계하여 피벗된 형태로 반환합니다.")
    @GetMapping("/daily-buckets")
    public ResponseEntity<HealthDtos.GlobalDailyStatsResponse> getGlobalDailyStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(healthService.getGlobalDailyStats(date));
    }
}
