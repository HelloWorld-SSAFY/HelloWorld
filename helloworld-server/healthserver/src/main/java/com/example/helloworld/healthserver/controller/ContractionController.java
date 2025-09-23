package com.example.helloworld.healthserver.controller;

import com.example.helloworld.healthserver.config.UserPrincipal;
import com.example.helloworld.healthserver.dto.request.CsCreateRequest;
import com.example.helloworld.healthserver.dto.response.*;
import com.example.helloworld.healthserver.service.ContractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@Tag(name = "Contractions", description = "진통 세션 API")
@RestController
@RequestMapping("/api/contractions")
@RequiredArgsConstructor
public class ContractionController {

    private final ContractionService contractionService;

    @Operation(summary = "진통 세션 생성", description = "시작/종료로 세션 생성. duration/interval 자동 계산")
    @PostMapping
    public ResponseEntity<CsCreateResponse> create(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody CsCreateRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contractionService.create(userPrincipal.getCoupleId(), req));
    }

    @Operation(summary = "진통 세션 목록", description = "기간 없으면 전체 내림차순")
    @GetMapping
    public ResponseEntity<CsListResponse> list(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ResponseEntity.ok(contractionService.list(userPrincipal.getCoupleId(), from, to));
    }
}
