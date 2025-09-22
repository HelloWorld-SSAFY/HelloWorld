package com.example.helloworld.userserver.member.controller;

import com.example.helloworld.userserver.member.dto.request.AvatarUrlRequest;
import com.example.helloworld.userserver.member.dto.request.MemberRegisterRequest;
import com.example.helloworld.userserver.member.dto.response.AvatarUrlResponse;
import com.example.helloworld.userserver.member.dto.response.MemberProfileResponse;
import com.example.helloworld.userserver.member.dto.response.MemberRegisterResponse;
import com.example.helloworld.userserver.member.service.MemberService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Users", description = "회원 등록/조회/수정 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class MemberController {

    private static final Logger log = LoggerFactory.getLogger(MemberController.class);

    private final MemberService memberService;

    /**
     * 인증된 사용자 확인 헬퍼
     */
    private InternalPrincipal requireAuth(InternalPrincipal principal) {
        if (principal == null || principal.memberId() == null) {
            log.warn("Unauthorized access attempt to {}",
                    Thread.currentThread().getStackTrace()[2].getMethodName());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal;
    }

    /**
     * 커플 권한 확인 헬퍼
     */
    private InternalPrincipal requireCouple(InternalPrincipal principal) {
        requireAuth(principal);
        if (principal.coupleId() == null) {
            log.warn("Couple registration required for user: {}", principal.memberId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Couple registration required");
        }
        return principal;
    }

    @Operation(summary = "회원 등록", description = "신규 회원 정보를 등록합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "409", description = "이미 등록된 사용자")
    })
    @PostMapping("/register")
    public ResponseEntity<MemberRegisterResponse> register(
            @Valid @RequestBody MemberRegisterRequest req,
            @AuthenticationPrincipal InternalPrincipal principal
    ) {
        var auth = requireAuth(principal);

        try {
            memberService.register(auth.memberId(), req);
            log.info("Member registered: memberId={}", auth.memberId());
            return ResponseEntity.ok(MemberRegisterResponse.ok(auth.memberId()));
        } catch (Exception e) {
            log.error("Failed to register member: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Registration failed");
        }
    }

    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자의 정보를 조회합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "404", description = "사용자 없음")
    })
    @GetMapping("/me")
    public ResponseEntity<MemberProfileResponse> getMe(
            @AuthenticationPrincipal InternalPrincipal principal
    ) {
        var auth = requireAuth(principal);

        try {
            MemberProfileResponse profile = memberService.getMe(auth.memberId());

            // 민감 정보 로깅 주의
            log.debug("Profile retrieved for user: {}", auth.memberId());

            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            log.error("Failed to get profile for user {}: {}", auth.memberId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        }
    }

    @Operation(summary = "내 정보 수정", description = "사용자 정보를 수정합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @PutMapping("/me")
    public ResponseEntity<MemberRegisterResponse> updateMe(
            @Valid @RequestBody MemberProfileResponse.MemberUpdateRequest req,
            @AuthenticationPrincipal InternalPrincipal principal
    ) {
        var auth = requireAuth(principal);

        try {
            memberService.updateProfile(auth.memberId(), req);

            // Audit log for healthcare compliance
            log.info("AUDIT: Profile updated - memberId={}, timestamp={}",
                    auth.memberId(), System.currentTimeMillis());

            return ResponseEntity.ok(MemberRegisterResponse.ok(auth.memberId()));
        } catch (Exception e) {
            log.error("Failed to update profile for user {}: {}", auth.memberId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Update failed");
        }
    }

    @Operation(summary = "프로필 이미지 변경", description = "사용자의 프로필 이미지 URL을 변경합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 URL 형식"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "413", description = "이미지 크기 초과")
    })
    @PutMapping("/profile-image")
    public ResponseEntity<AvatarUrlResponse> putAvatarUrl(
            @Valid @RequestBody AvatarUrlRequest req,
            @AuthenticationPrincipal InternalPrincipal principal
    ) {
        var auth = requireAuth(principal);

        try {
            // Validate image URL format and size (if needed)
            if (req.imageUrl() != null && !isValidImageUrl(req.imageUrl())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image URL");
            }

            AvatarUrlResponse response = memberService.setAvatarUrl(auth.memberId(), req);

            log.info("Profile image updated for user: {}", auth.memberId());

            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update avatar for user {}: {}", auth.memberId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Avatar update failed");
        }
    }

    /**
     * 이미지 URL 유효성 검증
     */
    private boolean isValidImageUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        // Basic URL validation
        if (!url.matches("^https?://.*")) {
            return false;
        }

        // Check for supported image formats
        String lowerUrl = url.toLowerCase();
        return lowerUrl.endsWith(".jpg") ||
                lowerUrl.endsWith(".jpeg") ||
                lowerUrl.endsWith(".png") ||
                lowerUrl.endsWith(".gif") ||
                lowerUrl.endsWith(".webp") ||
                lowerUrl.contains("/image/") ||  // CDN URLs
                lowerUrl.contains("amazonaws.com"); // S3 URLs
    }
}