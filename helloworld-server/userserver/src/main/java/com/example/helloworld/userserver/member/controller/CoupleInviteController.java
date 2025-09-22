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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@Tag(name = "Couple Invites", description = "초대코드 발급/합류 API")
@RestController
@RequestMapping("/api/couples")
@RequiredArgsConstructor
public class CoupleInviteController {

    private final CoupleInviteService inviteService;
    private final JwtProvider jwtProvider;

    // 초대코드 발급 (여성)
    @PostMapping("/invite")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "초대코드 발급(여성 전용)",
            description = "요청 바디 없음. 서버 설정 TTL로 만료시간이 정해지며, 응답에 code와 expiresAt이 포함됩니다."
    )
    @ApiResponse(responseCode = "200", description = "발급 성공",
            content = @Content(schema = @Schema(implementation = InviteCodeIssueResponse.class)))
    public ResponseEntity<InviteCodeIssueResponse> issue(
            @RequestHeader(value = "X-Internal-User-Id", required = false) String internalUserId,
            @RequestHeader(value = "Authorization", required = false) String authz
    ) {
        Long memberId = resolveMemberId(internalUserId, authz);
        return ResponseEntity.ok(inviteService.issue(memberId));
    }

    // 초대코드 합류 (남성)
    @PostMapping("/join")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "초대코드로 커플 합류(남성)")
    @ApiResponse(responseCode = "200", description = "연동 성공",
            content = @Content(schema = @Schema(implementation = CoupleJoinResponse.class)))
    public ResponseEntity<CoupleJoinResponse> join(
            @RequestHeader(value = "X-Internal-User-Id", required = false) String internalUserId,
            @RequestHeader(value = "Authorization", required = false) String authz,
            @Valid @RequestBody CoupleJoinRequest req
    ) {
        Long memberId = resolveMemberId(internalUserId, authz);
        return ResponseEntity.ok(inviteService.join(memberId, req));
    }

    // 커플 연동 해제
    @DeleteMapping("/divorce")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "커플 연동 해제", description = "여성(userA) 또는 남성(userB) 당사자만 가능. 해제 후 userB=null")
    @ApiResponse(responseCode = "200", description = "해제 성공",
            content = @Content(schema = @Schema(implementation = CoupleUnlinkResponse.class)))
    public ResponseEntity<CoupleUnlinkResponse> unlink(
            @RequestHeader(value = "X-Internal-User-Id", required = false) String internalUserId,
            @RequestHeader(value = "Authorization", required = false) String authz
    ) {
        Long memberId = resolveMemberId(internalUserId, authz);
        return ResponseEntity.ok(inviteService.unlink(memberId));
    }

    /** 게이트웨이 내부 헤더가 있으면 그걸 사용, 없으면 로컬 테스트용으로 JWT 파싱 */
    private Long resolveMemberId(String internalUserId, String authz) {
        if (internalUserId != null && !internalUserId.isBlank()) {
            return Long.valueOf(internalUserId);
        }
        return jwtProvider.parseAccessSubject(extractBearer(authz));
    }

    private static String extractBearer(String authz) {
        if (authz == null)
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Missing Authorization header");
        String token = authz.replaceFirst("(?i)^Bearer\\s+", "").trim();
        if (token.isEmpty())
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Empty token");
        return token;
    }
}

