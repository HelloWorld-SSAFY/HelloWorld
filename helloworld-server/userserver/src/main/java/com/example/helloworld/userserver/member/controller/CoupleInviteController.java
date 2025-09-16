package com.example.helloworld.userserver.member.controller;

import com.example.helloworld.userserver.auth.jwt.JwtProvider;
import com.example.helloworld.userserver.member.dto.request.CoupleJoinRequest;
import com.example.helloworld.userserver.member.dto.response.CoupleJoinResponse;
import com.example.helloworld.userserver.member.dto.response.CoupleUnlinkResponse;
import com.example.helloworld.userserver.member.dto.response.InviteCodeIssueResponse;
import com.example.helloworld.userserver.member.service.CoupleInviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Couple Invites", description = "초대코드 발급/합류 API")
@RestController
@RequestMapping("/api/couples")
@RequiredArgsConstructor
public class CoupleInviteController {

    private final CoupleInviteService inviteService;
    private final JwtProvider jwtProvider;

    // 발급
    @PostMapping("/invite")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "초대코드 발급(여성 전용)",
            description = "요청 바디 없음. 서버 설정 TTL로 만료시간이 정해지며, 응답에 code와 expiresAt이 포함됩니다."
    )
    @ApiResponse(responseCode = "200", description = "발급 성공",
            content = @Content(schema = @Schema(implementation = InviteCodeIssueResponse.class)))
    public ResponseEntity<InviteCodeIssueResponse> issue(
            @RequestHeader("Authorization") String authz
    ) {
        Long memberId = jwtProvider.parseAccessSubject(extractBearer(authz));
        return ResponseEntity.ok(inviteService.issue(memberId));
    }

    // 합류
    @PostMapping("/join")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "초대코드로 커플 합류(남성)")
    @ApiResponse(responseCode = "200", description = "연동 성공",
            content = @Content(schema = @Schema(implementation = CoupleJoinResponse.class)))
    public ResponseEntity<CoupleJoinResponse> join(
            @RequestHeader("Authorization") String authz,
            @Valid @RequestBody CoupleJoinRequest req
    ) {
        Long memberId = jwtProvider.parseAccessSubject(extractBearer(authz));
        return ResponseEntity.ok(inviteService.join(memberId, req));
    }

    @DeleteMapping("/divorce")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "커플 연동 해제", description = "여성(userA) 또는 남성(userB) 당사자만 가능. 해제 후 userB=null")
    @ApiResponse(responseCode = "200", description = "해제 성공",
            content = @Content(schema = @Schema(implementation = CoupleUnlinkResponse.class)))
    public ResponseEntity<CoupleUnlinkResponse> unlink(
            @RequestHeader("Authorization") String authz
    ) {
        Long memberId = jwtProvider.parseAccessSubject(extractBearer(authz));
        return ResponseEntity.ok(inviteService.unlink(memberId));
    }

    private static String extractBearer(String authz) {
        if (authz == null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "Missing Authorization header");
        String token = authz.replaceFirst("(?i)^Bearer\\s+", "").replaceAll("\\s+", "");
        if (token.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "Empty token");
        return token;
    }
}

