package com.example.helloworld.userserver.member.controller;

import com.example.helloworld.userserver.auth.jwt.JwtProvider;
import com.example.helloworld.userserver.member.dto.request.CoupleUpdateRequest;
import com.example.helloworld.userserver.member.dto.response.CoupleResponse;
import com.example.helloworld.userserver.member.dto.request.CoupleCreateRequest; // 새 DTO (생성 시 사용)
import com.example.helloworld.userserver.member.dto.response.CoupleWithUsersResponse;
import com.example.helloworld.userserver.member.service.CoupleService;
import com.example.helloworld.userserver.member.util.InternalPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Couples", description = "커플 생성/조회/수정/참여 API")
@RestController
@RequestMapping("/api/couples")
@RequiredArgsConstructor
public class CoupleController {

    private final CoupleService coupleService;

    @Operation(summary = "내 커플(상세) 조회: 커플 + 양쪽 유저")
    @GetMapping("/me/detail")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CoupleWithUsersResponse> getMyCoupleDetail(
            @AuthenticationPrincipal InternalPrincipal me
    ) {
        return ResponseEntity.ok(coupleService.getMyCoupleWithUsers(me.memberId()));
    }

    @Operation(summary = "커플 생성 (여성만 허용, userA=본인)")
    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CoupleResponse> create(
            @AuthenticationPrincipal InternalPrincipal me,
            @Valid @RequestBody CoupleCreateRequest req
    ) {
        CoupleResponse created = coupleService.createByFemale(me.memberId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(summary = "커플 공유 정보 수정")
    @PutMapping("/me/couple")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CoupleResponse> updateMyCouple(
            @AuthenticationPrincipal InternalPrincipal me,
            @Valid @RequestBody CoupleUpdateRequest req
    ) {
        return ResponseEntity.ok(coupleService.updateMyCouple(me.memberId(), req));
    }
}

