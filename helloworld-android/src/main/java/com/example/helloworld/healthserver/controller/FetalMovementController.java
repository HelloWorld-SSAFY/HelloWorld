package com.example.helloworld.healthserver.controller;

import com.example.helloworld.healthserver.config.UserPrincipal;
import com.example.helloworld.healthserver.dto.request.FmCreateRequest;
import com.example.helloworld.healthserver.dto.response.*;
import com.example.helloworld.healthserver.service.FetalMovementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;

@Tag(name = "Fetal Movement", description = "태동 기록 API")
@RestController
@RequestMapping("/api/fetal-movement")
@RequiredArgsConstructor
public class FetalMovementController {

    private final FetalMovementService fetalService;

    @Operation(summary = "태동 일별 조회", description = "from/to(YYYY-MM-DD) 범위에서 일별 개수 집계")
    @GetMapping
    public ResponseEntity<FmListResponse> list(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(fetalService.listDaily(userPrincipal.getCoupleId(), from, to));
    }

    @Operation(summary = "태동 기록 생성")
    @PostMapping
    public ResponseEntity<FmCreateResponse> create(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody FmCreateRequest req
    ) {
        var res = fetalService.create(userPrincipal.getCoupleId(), req);
        return ResponseEntity.created(URI.create("/api/fetal-movement")).body(res);
    }
}