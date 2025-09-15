package com.example.helloworld.userserver.member.controller;

import com.example.helloworld.userserver.auth.jwt.JwtProvider;
import com.example.helloworld.userserver.member.dto.MemberRegisterRequest;
import com.example.helloworld.userserver.member.dto.MemberRegisterResponse;
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

