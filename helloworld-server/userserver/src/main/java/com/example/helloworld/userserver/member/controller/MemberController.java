package com.example.helloworld.userserver.member.controller;

import com.example.helloworld.userserver.auth.jwt.JwtProvider;
import com.example.helloworld.userserver.member.dto.request.AvatarUrlRequest;
import com.example.helloworld.userserver.member.dto.request.CoupleUpdateRequest;
import com.example.helloworld.userserver.member.dto.request.MemberRegisterRequest;
import com.example.helloworld.userserver.member.dto.response.AvatarUrlResponse;
import com.example.helloworld.userserver.member.dto.response.MemberProfileResponse;
import com.example.helloworld.userserver.member.dto.response.MemberRegisterResponse;
import com.example.helloworld.userserver.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@Tag(name = "Users", description = "유저 등록/정보 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService userService;
    private final JwtProvider jwtProvider;

    @Operation(
            summary = "회원 등록/갱신 (여성은 커플 자동 생성)",
            description = """
        액세스 토큰 인증 후 내 회원 프로필을 등록/갱신합니다.
        
        - gender가 "female"이면 커플 레코드를 자동 생성(또는 기존 레코드 재사용)하고, userA=본인, userB=null로 둡니다.
        - 남성 파트너 연동은 '초대코드' 별도 API에서 처리합니다(본 API에서는 파트너를 지정하지 않습니다).
        """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = MemberRegisterRequest.class),
                            examples = @ExampleObject(
                                    value = """
                    {
                      "nickname": "박순자",
                      "gender": "female",
                      "age": 29,
                      "menstrual_date": "2025-09-01",
                      "is_childbirth": false,
                      "pregnancyWeek": 12,
                      "due_date": "2026-02-01"
                    }
                    """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "등록/갱신 성공",
                            content = @Content(schema = @Schema(implementation = MemberRegisterResponse.class))),
                    @ApiResponse(responseCode = "400", description = "유효성 실패"),
                    @ApiResponse(responseCode = "401", description = "인증 실패"),
                    @ApiResponse(responseCode = "409", description = "닉네임 중복"),
                    @ApiResponse(responseCode = "500", description = "서버 오류")
            }
    )
    @PostMapping("/register")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MemberRegisterResponse> register(
            @RequestHeader("Authorization") String authz,
            @Valid @RequestBody MemberRegisterRequest req
    ) {
        String token = extractBearer(authz);
        Long memberId = jwtProvider.parseAccessSubject(token);
        Long coupleId = userService.registerAndUpsertCouple(memberId, req);
        return ResponseEntity.ok(MemberRegisterResponse.ok(memberId, coupleId));
    }

    @PatchMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "내 프로필 부분 수정",
            description = "null이 아닌 필드만 수정합니다. 닉네임 중복·성별 변경 정책은 서비스에서 검증."
    )
    @ApiResponse(responseCode = "200", description = "수정 성공",
            content = @Content(schema = @Schema(implementation = MemberRegisterResponse.class)))
    public ResponseEntity<MemberRegisterResponse> updateMe(
            @RequestHeader("Authorization") String authz,
            @Valid @RequestBody MemberProfileResponse.MemberUpdateRequest req
    ) {
        String token = extractBearer(authz);
        Long memberId = jwtProvider.parseAccessSubject(token);
        Long coupleId = userService.updateProfile(memberId, req);
        return ResponseEntity.ok(MemberRegisterResponse.ok(memberId, coupleId));
    }

    @PatchMapping("/me/couple")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "커플 공유 정보 수정",
            description = "임신 주차(pregnancyWeek), 출산 예정일(due_date)을 수정합니다. 기본 정책: 여성(userA)만 허용."
    )
    @ApiResponse(responseCode = "200", description = "수정 성공",
            content = @Content(schema = @Schema(implementation = MemberRegisterResponse.class)))
    public ResponseEntity<MemberRegisterResponse> updateMyCouple(
            @RequestHeader("Authorization") String authz,
            @Valid @RequestBody CoupleUpdateRequest req
    ) {
        String token = extractBearer(authz);
        Long memberId = jwtProvider.parseAccessSubject(token);
        Long coupleId = userService.updateCoupleSharing(memberId, req);
        return ResponseEntity.ok(MemberRegisterResponse.ok(memberId, coupleId));
    }

    @Operation(
            summary = "내 정보 통합 조회 (프로필+커플)",
            description = "액세스 토큰 기준으로 회원 프로필과 커플/파트너 정보를 한 번에 반환합니다."
    )
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = MemberProfileResponse.class)))
    @GetMapping("/info")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MemberProfileResponse> getMe(
            @RequestHeader("Authorization") String authz
    ) {
        String token = extractBearer(authz);
        Long memberId = jwtProvider.parseAccessSubject(token);
        return ResponseEntity.ok(userService.getMyOverview(memberId));
    }

    @PutMapping("/profile-image")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "프로필 이미지 URL 설정(등록/변경/해제)",
            description = "기본 url은 '', put으로 바꾸기만 하면 됨."
    )
    @ApiResponse(responseCode = "200", description = "설정 성공",
            content = @Content(schema = @Schema(implementation = AvatarUrlResponse.class)))
    public ResponseEntity<AvatarUrlResponse> putAvatarUrl(
            @RequestHeader("Authorization") String authz,
            @RequestBody AvatarUrlRequest req
    ) {
        String token = extractBearer(authz);
        Long memberId = jwtProvider.parseAccessSubject(token);
        return ResponseEntity.ok(userService.setAvatarUrl(memberId, req));
    }


    private static String extractBearer(String authz) {
        if (authz == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Missing Authorization header");
        }
        String token = authz.replaceFirst("(?i)^Bearer\\s+", "");
        token = token.replaceAll("\\s+", "");
        if (token.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Empty token");
        }
        return token;
    }
}

