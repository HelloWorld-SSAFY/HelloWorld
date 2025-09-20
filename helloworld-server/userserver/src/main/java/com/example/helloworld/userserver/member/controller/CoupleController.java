package com.example.helloworld.userserver.member.controller;

import com.example.helloworld.userserver.auth.jwt.JwtProvider;
import com.example.helloworld.userserver.member.dto.request.CoupleUpdateRequest;
import com.example.helloworld.userserver.member.dto.response.CoupleResponse;
import com.example.helloworld.userserver.member.dto.request.CoupleCreateRequest; // 새 DTO (생성 시 사용)
import com.example.helloworld.userserver.member.service.CoupleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Couples", description = "커플 생성/조회/수정/참여 API")
@RestController
@RequestMapping("/api/couples")
@RequiredArgsConstructor
public class CoupleController {

    private final CoupleService coupleService;
    private final JwtProvider jwt;

    @Operation(summary = "내 커플 조회")
    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CoupleResponse> getMyCouple(@RequestHeader("Authorization") String authz) {
        Long uid = jwt.parseAccessSubject(extractBearer(authz));
        return ResponseEntity.ok(coupleService.getMyCouple(uid));
    }


    @Operation(summary = "커플 생성 (여성만 허용, userA=본인)")
    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CoupleResponse> create(@RequestHeader("Authorization") String authz,
                                                 @Valid @RequestBody CoupleCreateRequest req) {
        Long uid = jwt.parseAccessSubject(extractBearer(authz));
        CoupleResponse created = coupleService.createByFemale(uid, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(summary = "커플 공유 정보 수정")
    @PutMapping("/me/couple")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CoupleResponse> updateMyCouple(
            @RequestHeader("Authorization") String authz,
            @Valid @RequestBody CoupleUpdateRequest req
    ) {
        Long memberId = jwt.parseAccessSubject(extractBearer(authz));
        return ResponseEntity.ok(coupleService.updateMyCouple(memberId, req));
    }


    private static String extractBearer(String authz) {
        if (authz == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Missing Authorization header");
        }
        String token = authz.replaceFirst("(?i)^Bearer\\s+", "").replaceAll("\\s+", "");
        if (token.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Empty token");
        }
        return token;
    }
}

