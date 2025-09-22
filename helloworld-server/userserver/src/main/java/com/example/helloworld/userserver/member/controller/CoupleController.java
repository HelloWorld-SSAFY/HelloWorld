package com.example.helloworld.userserver.member.controller;

import com.example.helloworld.userserver.auth.jwt.JwtProvider;
import com.example.helloworld.userserver.member.dto.request.CoupleUpdateRequest;
import com.example.helloworld.userserver.member.dto.response.CoupleResponse;
import com.example.helloworld.userserver.member.dto.request.CoupleCreateRequest; // 새 DTO (생성 시 사용)
import com.example.helloworld.userserver.member.dto.response.CoupleWithUsersResponse;
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

    @Operation(summary = "내 커플(상세) 조회: 커플 + 양쪽 유저")
    @GetMapping("/me/detail")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CoupleWithUsersResponse> getMyCoupleDetail(
            @RequestHeader(value = "X-Internal-User-Id", required = false) String internalUserId,
            @RequestHeader(value = "Authorization", required = false) String authz
    ) {
        Long uid = resolveMemberId(internalUserId, authz);

        return ResponseEntity.ok(coupleService.getMyCoupleWithUsers(uid));
    }

    @Operation(summary = "커플 생성 (여성만 허용, userA=본인)")
    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CoupleResponse> create(
            @RequestHeader(value = "X-Internal-User-Id", required = false) String internalUserId,
            @RequestHeader(value = "Authorization", required = false) String authz,
            @Valid @RequestBody CoupleCreateRequest req
    ) {
        Long uid = resolveMemberId(internalUserId, authz);
        CoupleResponse created = coupleService.createByFemale(uid, req); // userA=본인
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(summary = "커플 공유 정보 수정")
    @PutMapping("/me/couple")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CoupleResponse> updateMyCouple(
            @RequestHeader(value = "X-Internal-User-Id", required = false) String internalUserId,
            @RequestHeader(value = "Authorization", required = false) String authz,
            @Valid @RequestBody CoupleUpdateRequest req
    ) {
        Long memberId = resolveMemberId(internalUserId, authz);
        return ResponseEntity.ok(coupleService.updateMyCouple(memberId, req));
    }

    private Long resolveMemberId(String internalUserId, String authz) {
        if (internalUserId != null && !internalUserId.isBlank()) {
            return Long.valueOf(internalUserId);
        }
        // 게이트웨이 우회(로컬 테스트)일 때만 JWT 파싱
        return jwt.parseAccessSubject(extractBearer(authz));
    }

    private static String extractBearer(String authz) {
        if (authz == null) throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Missing Authorization header");
        String token = authz.replaceFirst("(?i)^Bearer\\s+", "").trim();
        if (token.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Empty token");
        return token;
    }
}

