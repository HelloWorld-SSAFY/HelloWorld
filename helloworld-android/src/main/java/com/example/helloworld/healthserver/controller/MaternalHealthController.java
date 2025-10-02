package com.example.helloworld.healthserver.controller;


import com.example.helloworld.healthserver.config.UserPrincipal;
import com.example.helloworld.healthserver.dto.MHDtos.*;
import com.example.helloworld.healthserver.dto.response.*;
import com.example.helloworld.healthserver.service.MaternalHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "Maternal Health", description = "임산부 건강 기록 API")
@RestController
@RequestMapping("/api/maternal-health")
@RequiredArgsConstructor
public class MaternalHealthController {

    private final MaternalHealthService mhService;

    @Operation(summary = "임산부 건강 단건 조회")
    @GetMapping("/{maternalId}")
    public ResponseEntity<MhGetResponse> getById(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long maternalId
    ) {
        return ResponseEntity.ok(mhService.getById(userPrincipal.getCoupleId(), maternalId));
    }

    @Operation(summary = "임산부 건강 생성", description = "서버의 app.zone(기본 Asia/Seoul) 오늘 날짜로 저장")
    @PostMapping
    public ResponseEntity<Void> create(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody MhCreateRequest req
    ) {
        mhService.create(userPrincipal.getCoupleId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "임산부 건강 수정", description = "blood_pressure는 'NNN/NNN'")
    @PutMapping("/{maternalId}")
    public ResponseEntity<MhUpdateResponse> update(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long maternalId,
            @Valid @RequestBody MhUpdateRequest req
    ) {
        return ResponseEntity.ok(mhService.update(userPrincipal.getCoupleId(), maternalId, req));
    }

    @Operation(summary = "임산부 건강 삭제")
    @DeleteMapping("/{maternalId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long maternalId
    ) {
        mhService.delete(userPrincipal.getCoupleId(), maternalId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "임산부 건강 목록", description = "기본 내림차순. 페이지네이션 없음")
    @GetMapping
    public ResponseEntity<MhListResponse> list(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(mhService.list(userPrincipal.getCoupleId(), from, to));
    }
}
