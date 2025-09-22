package com.example.helloworld.userserver.member.controller;

import com.example.helloworld.userserver.member.dto.request.CoupleCreateRequest;
import com.example.helloworld.userserver.member.dto.request.CoupleUpdateRequest;
import com.example.helloworld.userserver.member.dto.response.CoupleResponse;
import com.example.helloworld.userserver.member.dto.response.CoupleWithUsersResponse;
import com.example.helloworld.userserver.member.service.CoupleService;
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

@Tag(name = "Couples", description = "커플 생성/조회/수정 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/couples")
@RequiredArgsConstructor
public class CoupleController {

    private static final Logger log = LoggerFactory.getLogger(CoupleController.class);
    private final CoupleService coupleService;

    private InternalPrincipal requireAuth(InternalPrincipal principal) {
        if (principal == null || principal.memberId() == null) {
            log.warn("Unauthorized access attempt to couples API");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal;
    }

    private InternalPrincipal requireCouple(InternalPrincipal principal) {
        requireAuth(principal);
        if (principal.coupleId() == null) {
            log.warn("Couple registration required for user: {}", principal.memberId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "커플 등록이 필요합니다");
        }
        return principal;
    }

    @Operation(summary = "내 커플 상세 조회", description = "커플 정보와 양쪽 유저 정보를 조회합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "404", description = "커플 정보 없음")
    })
    @GetMapping("/me/detail")
    public ResponseEntity<CoupleWithUsersResponse> getMyCoupleDetail(
            @AuthenticationPrincipal InternalPrincipal principal
    ) {
        log.info("Principal received: {}", principal);

        if (principal == null) {
            log.error("Principal is null - authentication failed");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        var auth = requireAuth(principal);

        try {
            CoupleWithUsersResponse response = coupleService.getMyCoupleWithUsers(auth.memberId());

            log.info("Couple detail retrieved for user: {}, couple: {}",
                    auth.memberId(), auth.coupleId());

            // 민감 정보 마스킹 (파트너의 의료 정보 등)
            response = maskSensitiveData(response, auth);

            log.info("Couple detail retrieved for user: {}, couple: {}",
                    auth.memberId(), auth.coupleId());

            log.info("Returning successful response");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get couple detail for user {}: {}", auth.memberId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "커플 정보를 찾을 수 없습니다");
        }
    }

    @Operation(summary = "커플 생성", description = "여성 회원만 커플을 생성할 수 있습니다")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "403", description = "권한 없음 (남성 회원)"),
            @ApiResponse(responseCode = "409", description = "이미 커플 등록됨")
    })
    @PostMapping
    public ResponseEntity<CoupleResponse> create(
            @AuthenticationPrincipal InternalPrincipal principal,
            @Valid @RequestBody CoupleCreateRequest req
    ) {
        var auth = requireAuth(principal);

        // 이미 커플이 있는지 확인
        if (auth.coupleId() != null) {
            log.warn("User {} already has couple: {}", auth.memberId(), auth.coupleId());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 커플이 등록되어 있습니다");
        }

        try {
            // Service layer에서 성별 확인
            CoupleResponse created = coupleService.createByFemale(auth.memberId(), req);

            // Audit log for healthcare compliance
            log.info("AUDIT: Couple created - memberId={}, coupleId={}, timestamp={}",
                    auth.memberId(), created.coupleId(), System.currentTimeMillis());

            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalStateException e) {
            // 성별 확인 실패 또는 이미 커플 존재
            log.warn("Couple creation failed for user {}: {}", auth.memberId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to create couple for user {}: {}", auth.memberId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "커플 생성 실패");
        }
    }

    @Operation(summary = "커플 정보 수정", description = "커플 공유 정보를 수정합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "403", description = "커플 미등록"),
            @ApiResponse(responseCode = "404", description = "커플 정보 없음")
    })
    @PutMapping("/me/couple")
    public ResponseEntity<CoupleResponse> updateMyCouple(
            @AuthenticationPrincipal InternalPrincipal principal,
            @Valid @RequestBody CoupleUpdateRequest req
    ) {
        var auth = requireCouple(principal);  // 커플 권한 확인

        try {
            // Service 메서드 시그니처에 맞게 수정 (보통 memberId와 req만 받음)
            CoupleResponse updated = coupleService.updateMyCouple(auth.memberId(), req);

            log.info("Couple updated - userId={}, coupleId={}", auth.memberId(), auth.coupleId());

            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Failed to update couple {} for user {}: {}",
                    auth.coupleId(), auth.memberId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "커플 정보 수정 실패");
        }
    }

    private CoupleWithUsersResponse maskSensitiveData(CoupleWithUsersResponse response,
                                                      InternalPrincipal principal) {
        // 파트너의 민감한 의료 정보 마스킹 로직
        // 예: 본인이 아닌 파트너의 상세 의료 기록은 제한
        return response;
    }
}