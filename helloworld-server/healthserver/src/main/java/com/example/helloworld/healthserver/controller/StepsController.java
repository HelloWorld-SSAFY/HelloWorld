package com.example.helloworld.healthserver.controller;


import com.example.helloworld.healthserver.config.UserPrincipal;
import com.example.helloworld.healthserver.dto.HealthDtos;
import com.example.helloworld.healthserver.dto.StepsDtos.CreateRequest;
import com.example.helloworld.healthserver.dto.StepsDtos.CreateResponse;
import com.example.helloworld.healthserver.service.HealthDataService;
import com.example.helloworld.healthserver.service.StepsDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

@Tag(name = "Steps", description = "걸음수 등록 API")
@RestController
@RequestMapping("/api/steps")
@RequiredArgsConstructor
public class StepsController {

    private final StepsDataService service;
    private final HealthDataService healthService;

    @Operation(
            summary = "걸음수 등록",
            description = "특정 커플(couple_id)의 걸음수를 한 건 등록합니다.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Created"),
                    @ApiResponse(responseCode = "400", description = "Bad Request"),
                    @ApiResponse(responseCode = "404", description = "Not Found")
            }
    )

    @PostMapping
    public ResponseEntity<CreateResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateRequest req
    ) {
        // 커플 권한 확인
        if (!principal.hasCouple()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "커플 등록이 필요합니다");
        }

        CreateResponse res = service.create(principal.getCoupleId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

//    @GetMapping("/my-steps")
//    public ResponseEntity<List<CreateResponse>> getMySteps(
//            @AuthenticationPrincipal UserPrincipal principal,
//            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
//    ) {
//        if (!principal.hasCouple()) {
//            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
//        }
//
//        // 해당 날짜의 걸음수 조회
//        return ResponseEntity.ok(
//                service.getStepsByDate(principal.getCoupleId(), date)
//        );
//    }

    @Operation(summary = "걸음수 누적 평균(전기간)", description = "구간: 00-12, 00-16. steps>0만 포함.")
    @GetMapping("/overall-cumulative-avg")
    public ResponseEntity<HealthDtos.StepResponse> overallCumulativeAvg(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(healthService.overallCumulativeAvg(principal.getCoupleId()));
    }
}