package com.example.helloworld.healthserver.controller;


import io.swagger.v3.oas.annotations.Parameter;
import com.example.helloworld.healthserver.config.UserPrincipal;
import com.example.helloworld.healthserver.dto.StepsDtos;
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
import org.springframework.web.bind.annotation.RequestBody;
import io.swagger.v3.oas.annotations.media.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Steps", description = "걸음수 등록 API")
@Slf4j
@RestController
@RequestMapping("/api/steps")
@RequiredArgsConstructor
public class StepsController {

    private final StepsDataService service;


    @Operation(
            summary = "걸음수 등록 및 이상탐지",
            description = "걸음수를 등록하고 AI 서버로 이상탐지 요청을 보냅니다.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Created"),
                    @ApiResponse(responseCode = "400", description = "Bad Request"),
                    @ApiResponse(responseCode = "502", description = "AI Server Error")
            }
    )
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StepsDtos.CreateWithAnomalyResponse> create(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody StepsDtos.CreateRequest req
    ) {
        // 인증/커플 검증 (NPE 방지)
        if (principal == null || principal.getCoupleId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not associated with a couple.");
        }

        log.info("Steps creation request - userId: {}, coupleId: {}", principal.getUserId(), principal.getCoupleId());

        // 저장 + AI 호출 (서비스에서 모든 예외를 적절한 ResponseStatusException으로 변환)
        StepsDtos.CreateWithAnomalyResponse response = service.createAndCheck(principal, req);

        log.info("Steps created and checked - stepsId: {}, anomaly: {}", response.stepsId(), response.anomaly());

        // Location 헤더(optional): 리소스 조회 엔드포인트가 있다면 포함
        // URI location = URI.create("/api/steps/" + response.stepsId());
        // return ResponseEntity.created(location).body(response);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
    public ResponseEntity<StepsDtos.StepResponse> overallCumulativeAvg(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(service.overallCumulativeAvg(principal.getCoupleId()));
    }
}