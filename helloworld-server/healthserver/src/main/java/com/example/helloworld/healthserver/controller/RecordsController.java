package com.example.helloworld.healthserver.controller;

import com.example.helloworld.healthserver.dto.MHDtos.*;
import com.example.helloworld.healthserver.dto.request.CsCreateRequest;
import com.example.helloworld.healthserver.dto.request.FmCreateRequest;
import com.example.helloworld.healthserver.dto.response.*;
import com.example.helloworld.healthserver.service.ContractionService;
import com.example.helloworld.healthserver.service.FetalMovementService;
import com.example.helloworld.healthserver.service.MaternalHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;

@Tag(name = "Records", description = "태동/진통/임산부 건강 기록 API")
@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class RecordsController {

    private final FetalMovementService fetalService;
    private final ContractionService contractionService;
    private final MaternalHealthService mhService;

    // ---------- FETAL MOVEMENT ----------
    @Operation(summary = "태동 일별 조회", description = "from/to(YYYY-MM-DD) 범위에서 일별 개수 집계")
    @GetMapping("/fetal-movement")
    public ResponseEntity<FmListResponse> listFetal(
            @RequestParam Long coupleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(fetalService.listDaily(coupleId, from, to));
    }

    @Operation(summary = "태동 기록 생성", responses = @ApiResponse(responseCode = "201"))
    @PostMapping("/fetal-movement")
    public ResponseEntity<FmCreateResponse> createFetal(
            @RequestParam Long coupleId,
            @Valid @RequestBody FmCreateRequest req
    ) {
        FmCreateResponse res = fetalService.create(coupleId, req);
        return ResponseEntity.created(URI.create("/api/records/fetal-movement"))
                .body(res);
    }

    // ---------- CONTRACTIONS ----------
    @Operation(summary = "진통 세션 생성", description = "시작/종료로 세션 생성. duration/interval 자동 계산")
    @PostMapping("/contractions")
    public ResponseEntity<CsCreateResponse> createContraction(
            @RequestParam Long coupleId,
            @Valid @RequestBody CsCreateRequest req
    ) {
        var res = contractionService.create(coupleId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @Operation(summary = "진통 세션 목록", description = "기간 없으면 전체 내림차순")
    @GetMapping("/contractions")
    public ResponseEntity<CsListResponse> listContractions(
            @RequestParam Long coupleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ResponseEntity.ok(contractionService.list(coupleId, from, to));
    }

    // ---------- MATERNAL HEALTH ----------
//    @Operation(summary = "임산부 건강 최신 조회")
//    @GetMapping("/maternal-health/latest")
//    public ResponseEntity<MhGetResponse> getLatest(@RequestParam Long coupleId) {
//        return ResponseEntity.ok(mhService.getLatest(coupleId));
//    }

    @Operation(summary = "임산부 건강 단건 조회", description = "maternalId(숫자)로 해당 기록을 조회")
    @GetMapping("/maternal-health/{maternalId}")
    public ResponseEntity<MhGetResponse> getMaternalById(
            @RequestParam Long coupleId,
            @PathVariable Long maternalId
    ) {
        return ResponseEntity.ok(mhService.getById(coupleId, maternalId));
    }

    @Operation(summary = "임산부 건강 생성", description = "서버의 app.zone(기본 Asia/Seoul) 오늘 날짜로 저장")
    @PostMapping("/maternal-health")
    public ResponseEntity<Void> createMH(
            @RequestParam Long coupleId,
            @Valid @RequestBody MhCreateRequest req
    ) {
        mhService.create(coupleId, req);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "임산부 건강 수정", description = "blood_pressure는 'NNN/NNN'")
    @PutMapping("/maternal-health/{maternalId}")
    public ResponseEntity<MhUpdateResponse> updateMH(
            @RequestParam Long coupleId,
            @PathVariable Long maternalId,
            @Valid @RequestBody MhUpdateRequest req
    ) {
        return ResponseEntity.ok(mhService.update(coupleId, maternalId, req));
    }

    @Operation(summary = "임산부 건강 삭제")
    @DeleteMapping("/maternal-health/{maternalId}")
    public ResponseEntity<Void> deleteMH(
            @RequestParam Long coupleId,
            @PathVariable Long maternalId
    ) {
        mhService.delete(coupleId, maternalId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "임산부 건강 목록", description = "기본 내림차순. 페이지네이션 없음")
    @GetMapping("/maternal-health")
    public ResponseEntity<MhListResponse> listMH(
            @RequestParam Long coupleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(mhService.list(coupleId, from, to));
    }
}
