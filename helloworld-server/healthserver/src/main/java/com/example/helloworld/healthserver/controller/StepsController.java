package com.example.helloworld.healthserver.controller;


import com.example.helloworld.healthserver.config.UserPrincipal;
import com.example.helloworld.healthserver.dto.HealthDtos;
import com.example.helloworld.healthserver.dto.StepsDtos;
import com.example.helloworld.healthserver.service.HealthDataService;
import com.example.helloworld.healthserver.service.StepsDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.media.*;
import java.net.URI;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Steps", description = "걸음수 등록 API")
@Slf4j
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
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = StepsDtos.CreateRequest.class),
                    examples = @ExampleObject(
                            name = "valid-steps-request",
                            value = """
      {
        "date": "2025-09-23T05:08:24.587Z",
        "steps": 4200,
        "latitude": 32.1,
        "longitude": 31.0
      }
      """
                    )
            )
    )
    @PostMapping
    public ResponseEntity<StepsDtos.CreateResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody StepsDtos.CreateRequest req
    ) {
        log.info("StepsController - principal class: {}", principal.getClass().getName());
        log.info("StepsController - userId: {}, coupleId: {}",
                principal.getUserId(), principal.getCoupleId());

        // 이 부분이 실행되는지 확인
        try {
            StepsDtos.CreateResponse res = service.create(principal.getCoupleId(), req);
            log.info("Steps created successfully: {}", res.stepsId());
            return ResponseEntity.status(HttpStatus.CREATED).body(res);
        } catch (ResponseStatusException e) {
            log.error("Service threw exception: status={}, reason={}",
                    e.getStatusCode(), e.getReason());
            throw e;
        }

//        StepsDtos.CreateResponse res = service.create(principal.getCoupleId(), req);
//        return ResponseEntity.status(HttpStatus.CREATED).body(res);
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

    @GetMapping("/api/_debug/auth")
    public Map<String,Object> me(@AuthenticationPrincipal UserPrincipal p, HttpServletRequest r) {
        return Map.of(
                "uri", r.getRequestURI(),
                "userId", p != null ? p.getUserId() : null,
                "coupleId", p != null ? p.getCoupleId() : null
        );
    }


}