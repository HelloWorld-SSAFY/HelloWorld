package com.example.helloworld.userserver.member.controller;

import com.example.helloworld.userserver.auth.jwt.JwtProvider;
import com.example.helloworld.userserver.member.dto.request.AvatarUrlRequest;
import com.example.helloworld.userserver.member.dto.request.MemberRegisterRequest;
import com.example.helloworld.userserver.member.dto.response.AvatarUrlResponse;
import com.example.helloworld.userserver.member.dto.response.MemberProfileResponse;
import com.example.helloworld.userserver.member.dto.response.MemberRegisterResponse;
import com.example.helloworld.userserver.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Users", description = "회원 등록/조회/수정 API")
@RestController
@RequestMapping("api/users")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final JwtProvider jwt;

    @Operation(
            summary = "회원 등록/갱신",
            description = "회원 정보(닉네임/성별/나이)만 등록 또는 갱신한다. 커플 생성은 별도 Couples API에서 처리.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = MemberRegisterRequest.class),
                            examples = @ExampleObject(
                                    value = """
                    {
                      "nickname": "장문복",
                      "gender": "female",
                      "age": 29
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
                    @ApiResponse(responseCode = "409", description = "닉네임 중복")
            }
    )
    @PostMapping("/register")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MemberRegisterResponse> register(
            @RequestHeader("Authorization") String authz,
            @Valid @RequestBody MemberRegisterRequest req
    ) {
        Long memberId = jwt.parseAccessSubject(extractBearer(authz));
        memberService.register(memberId, req);
        return ResponseEntity.ok(MemberRegisterResponse.ok(memberId));
    }

    @Operation(summary = "내 프로필 조회")
    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MemberProfileResponse> getMe(@RequestHeader("Authorization") String authz) {
        Long memberId = jwt.parseAccessSubject(extractBearer(authz));
        return ResponseEntity.ok(memberService.getMe(memberId));
    }

    @Operation(
            summary = "내 프로필 부분 수정",
            description = "null이 아닌 필드만 수정. 닉네임 중복 등은 서비스에서 검증."
    )
    @PutMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MemberRegisterResponse> updateMe(
            @RequestHeader("Authorization") String authz,
            @Valid @RequestBody MemberProfileResponse.MemberUpdateRequest req
    ) {
        Long memberId = jwt.parseAccessSubject(extractBearer(authz));
        memberService.updateProfile(memberId, req);
        return ResponseEntity.ok(MemberRegisterResponse.ok(memberId));
    }

    @Operation(summary = "프로필 이미지 URL 설정(등록/변경/해제)")
    @PutMapping("/profile-image")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<AvatarUrlResponse> putAvatarUrl(
            @RequestHeader("Authorization") String authz,
            @RequestBody AvatarUrlRequest req
    ) {
        Long memberId = jwt.parseAccessSubject(extractBearer(authz));
        return ResponseEntity.ok(memberService.setAvatarUrl(memberId, req));
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

