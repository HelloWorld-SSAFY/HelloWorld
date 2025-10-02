// ========== CoupleInviteController.java ==========
package com.example.helloworld.userserver.member.controller;

import com.example.helloworld.userserver.member.dto.request.CoupleJoinRequest;
import com.example.helloworld.userserver.member.dto.response.CoupleJoinResponse;
import com.example.helloworld.userserver.member.dto.response.CoupleUnlinkResponse;
import com.example.helloworld.userserver.member.dto.response.InviteCodeIssueResponse;
import com.example.helloworld.userserver.member.service.CoupleInviteService;
import com.example.helloworld.userserver.member.util.InternalPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

@Tag(name = "Couple Invites", description = "초대코드 발급/합류 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/couples")
@RequiredArgsConstructor
public class CoupleInviteController {

    private static final Logger log = LoggerFactory.getLogger(CoupleInviteController.class);
    private final CoupleInviteService inviteService;

    // Simple rate limiting without external dependencies
    private final ConcurrentHashMap<Long, RateLimitInfo> rateLimitMap = new ConcurrentHashMap<>();

    private InternalPrincipal requireAuth(InternalPrincipal principal) {
        if (principal == null || principal.memberId() == null) {
            log.warn("Unauthorized access attempt to invite API");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal;
    }

    private InternalPrincipal requireNoCouple(InternalPrincipal principal) {
        requireAuth(principal);
        if (principal.coupleId() != null) {
            log.warn("User {} already in couple: {}", principal.memberId(), principal.coupleId());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 커플이 등록되어 있습니다");
        }
        return principal;
    }

    @Operation(summary = "초대코드 발급", description = "파트너 초대를 위한 코드를 발급합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "발급 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "409", description = "이미 커플 존재"),
            @ApiResponse(responseCode = "429", description = "너무 많은 요청")
    })
    @PostMapping("/invite")
    public ResponseEntity<InviteCodeIssueResponse> issue(
            @AuthenticationPrincipal InternalPrincipal principal
    ) {
        var auth = requireAuth(principal);

        // Simple rate limiting: 시간당 5회로 제한
        if (!checkRateLimit(auth.memberId(), 5, 1)) {
            log.warn("Rate limit exceeded for user: {}", auth.memberId());
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "초대코드 발급은 시간당 5회로 제한됩니다");
        }

        try {
            InviteCodeIssueResponse response = inviteService.issue(auth.memberId());

            // 필드명이 'code'임
            log.info("Invite code issued for user: {}, code: {}",
                    auth.memberId(), response.code());

            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            log.warn("Invite code issue failed for user {}: {}", auth.memberId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to issue invite code for user {}: {}", auth.memberId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "초대코드 발급 실패");
        }
    }

    @Operation(summary = "커플 합류", description = "초대코드로 커플에 합류합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "합류 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 초대코드"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "409", description = "이미 커플 존재"),
            @ApiResponse(responseCode = "410", description = "만료된 초대코드")
    })
    @PostMapping("/join")
    public ResponseEntity<CoupleJoinResponse> join(
            @AuthenticationPrincipal InternalPrincipal principal,
            @Valid @RequestBody CoupleJoinRequest req
    ) {
        var auth = requireNoCouple(principal);  // 커플이 없어야 함

        try {
            // 필드명이 'code'임
            if (!isValidInviteCode(req.code())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 초대코드 형식");
            }

            CoupleJoinResponse response = inviteService.join(auth.memberId(), req);

            // Audit log
            log.info("AUDIT: User {} joined couple {} via invite code",
                    auth.memberId(), response.coupleId());

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid invite code used by user {}: {}", auth.memberId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 초대코드");
        } catch (IllegalStateException e) {
            log.warn("Join failed for user {}: {}", auth.memberId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.GONE, "만료되거나 사용된 초대코드");
        } catch (Exception e) {
            log.error("Failed to join couple for user {}: {}", auth.memberId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "커플 합류 실패");
        }
    }

    @Operation(summary = "커플 연결 해제", description = "커플 관계를 해제합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "해제 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "404", description = "커플 정보 없음")
    })
    @DeleteMapping("/divorce")
    public ResponseEntity<CoupleUnlinkResponse> unlink(
            @AuthenticationPrincipal InternalPrincipal principal
    ) {
        var auth = requireAuth(principal);

        if (auth.coupleId() == null) {
            log.warn("Unlink attempt by user {} with no couple", auth.memberId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "등록된 커플이 없습니다");
        }

        try {
            CoupleUnlinkResponse response = inviteService.unlink(auth.memberId());

            // Important audit log for relationship changes
            log.info("AUDIT: Couple unlinked - userId={}, coupleId={}, timestamp={}",
                    auth.memberId(), auth.coupleId(), System.currentTimeMillis());

            // 이벤트 발행 (MSA) - 실제 구현 필요
            // publishCoupleUnlinkedEvent(auth.memberId(), auth.coupleId());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to unlink couple for user {}: {}", auth.memberId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "커플 해제 실패");
        }
    }

    /**
     * Simple rate limiting implementation without external dependencies
     */
    private boolean checkRateLimit(Long memberId, int maxRequests, int hours) {
        Instant now = Instant.now();
        RateLimitInfo info = rateLimitMap.compute(memberId, (k, v) -> {
            if (v == null || v.windowStart.plus(hours, ChronoUnit.HOURS).isBefore(now)) {
                // 새로운 윈도우 시작
                return new RateLimitInfo(now, 1);
            } else {
                // 기존 윈도우에서 카운트 증가
                v.count++;
                return v;
            }
        });

        // 오래된 엔트리 정리 (메모리 관리)
        if (rateLimitMap.size() > 1000) {
            cleanupOldEntries(now, hours);
        }

        return info.count <= maxRequests;
    }

    private void cleanupOldEntries(Instant now, int hours) {
        rateLimitMap.entrySet().removeIf(entry ->
                entry.getValue().windowStart.plus(hours, ChronoUnit.HOURS).isBefore(now)
        );
    }

    private boolean isValidInviteCode(String code) {
        // 초대코드 형식 검증 (예: 6-8자리 영숫자)
        return code != null && code.matches("^[A-Z0-9]{6,8}$");
    }

    /**
     * Rate limiting info holder
     */
    private static class RateLimitInfo {
        final Instant windowStart;
        int count;

        RateLimitInfo(Instant windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}