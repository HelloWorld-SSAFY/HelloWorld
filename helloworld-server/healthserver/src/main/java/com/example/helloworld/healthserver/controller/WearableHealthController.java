package com.example.helloworld.healthserver.controller;

import com.example.helloworld.healthserver.dto.HealthDtos;
import com.example.helloworld.healthserver.service.HealthDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;

@Tag(name = "Wearable Health", description = "웨어러블 건강 데이터 API (심박/스트레스/통계)")
@RestController
@RequestMapping("/api/wearable")
@RequiredArgsConstructor
public class WearableHealthController {

    private final HealthDataService healthService;

//    @Operation(summary = "헬스데이터 생성")
//    @PostMapping
//    public ResponseEntity<HealthDtos.GetResponse> create(
//            @RequestParam Long coupleId,
//            @Valid @RequestBody HealthDtos.CreateRequest req
//    ) {
//        var res = healthService.create(coupleId, req);
//        return ResponseEntity.created(URI.create("/api/wearable/" + res.healthId())).body(res);
//    }

    @Operation(summary = "헬스데이터 단건 조회")
    @GetMapping("/{healthId}")
    public ResponseEntity<HealthDtos.GetResponse> getOne(
            @RequestParam Long coupleId,
            @PathVariable Long healthId
    ) {
        return ResponseEntity.ok(healthService.getById(coupleId, healthId));
    }

    @Operation(summary = "하루 4시간 버킷 통계", description = "쿼리 date=YYYY-MM-DD (KST). 심박수 45≤x<150만 포함.")
    @GetMapping("/daily-buckets")
    public ResponseEntity<HealthDtos.BucketResponse> dailyBuckets(
            @RequestParam Long coupleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(healthService.hrDailyBuckets(coupleId, date));
    }

//    @Operation(summary = "걸음수 누적 평균(전기간)", description = "구간: 00-12, 00-16. steps>0만 포함.")
//    @GetMapping("/overall-cumulative-avg")
//    public ResponseEntity<HealthDtos.StepResponse> overallCumulativeAvg(
//            @RequestParam Long coupleId
//    ) {
//        return ResponseEntity.ok(healthService.overallCumulativeAvg(coupleId));
//    }
}
