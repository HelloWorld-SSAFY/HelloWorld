package com.example.helloworld.healthserver.controller;


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
import org.springframework.web.bind.annotation.*;

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
    @PostMapping("/{couple_id}")
    public ResponseEntity<CreateResponse> create(
            // @RequestHeader("Authorization") String authz,    // JWT 붙일 경우 사용
            @PathVariable("couple_id") Long coupleId,
            @Valid @RequestBody CreateRequest req
    ) {
        CreateResponse res = service.create(coupleId, req);
        return ResponseEntity
                .created(URI.create("/api/steps/%d/%d".formatted(coupleId, res.stepsId())))
                .body(res);
    }

    @Operation(summary = "걸음수 단건 조회")
    @GetMapping("/{couple_id}/{steps_id}")
    public ResponseEntity<CreateResponse> getOne(
            // @RequestHeader("Authorization") String authz,
            @PathVariable("couple_id") Long coupleId,
            @PathVariable("steps_id") Long stepsId
    ) {
        return ResponseEntity.ok(service.getById(coupleId, stepsId));
    }

    @Operation(summary = "걸음수 누적 평균(전기간)", description = "구간: 00-12, 00-16. steps>0만 포함.")
    @GetMapping("/overall-cumulative-avg")
    public ResponseEntity<HealthDtos.StepResponse> overallCumulativeAvg(
            @RequestParam Long coupleId
    ) {
        return ResponseEntity.ok(healthService.overallCumulativeAvg(coupleId));
    }
}