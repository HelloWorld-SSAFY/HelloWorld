package com.example.helloworld.userserver.member.controller;

import com.example.helloworld.userserver.auth.jwt.JwtProvider;
import com.example.helloworld.userserver.member.dto.request.AvatarUrlRequest;
import com.example.helloworld.userserver.member.dto.request.MemberRegisterRequest;
import com.example.helloworld.userserver.member.dto.response.AvatarUrlResponse;
import com.example.helloworld.userserver.member.dto.response.MemberProfileResponse;
import com.example.helloworld.userserver.member.dto.response.MemberRegisterResponse;
import com.example.helloworld.userserver.member.service.MemberService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${gateway.hmac-secret}")
    private String hmacSecret; // 게이트웨이와 동일한 시크릿으로 검증

    @PostMapping("/register")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MemberRegisterResponse> register(
            // 게이트웨이 내부 헤더(있으면 이걸로 인증)
            @RequestHeader(value = "X-Internal-User-Id", required = false) String internalUserId,
            @RequestHeader(value = "X-Internal-Ts", required = false) String internalTs,
            @RequestHeader(value = "X-Internal-Sig", required = false) String internalSig,
            // 로컬/직접 호출용 폴백
            @RequestHeader(value = "Authorization", required = false) String authz,
            @Valid @RequestBody MemberRegisterRequest req
    ) {
        Long memberId = resolveMemberId(internalUserId, internalTs, internalSig, authz);

        memberService.register(memberId, req);
        return ResponseEntity.ok(MemberRegisterResponse.ok(memberId));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MemberProfileResponse> getMe(
            @RequestHeader(value = "X-Internal-User-Id", required = false) String internalUserId,
            @RequestHeader(value = "X-Internal-Ts", required = false) String internalTs,
            @RequestHeader(value = "X-Internal-Sig", required = false) String internalSig,
            @RequestHeader(value = "Authorization", required = false) String authz
    ) {
        Long memberId = resolveMemberId(internalUserId, internalTs, internalSig, authz);
        return ResponseEntity.ok(memberService.getMe(memberId));
    }

    @PutMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MemberRegisterResponse> updateMe(
            @RequestHeader(value = "X-Internal-User-Id", required = false) String internalUserId,
            @RequestHeader(value = "X-Internal-Ts", required = false) String internalTs,
            @RequestHeader(value = "X-Internal-Sig", required = false) String internalSig,
            @RequestHeader(value = "Authorization", required = false) String authz,
            @Valid @RequestBody MemberProfileResponse.MemberUpdateRequest req
    ) {
        Long memberId = resolveMemberId(internalUserId, internalTs, internalSig, authz);
        memberService.updateProfile(memberId, req);
        return ResponseEntity.ok(MemberRegisterResponse.ok(memberId));
    }

    @PutMapping("/profile-image")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<AvatarUrlResponse> putAvatarUrl(
            @RequestHeader(value = "X-Internal-User-Id", required = false) String internalUserId,
            @RequestHeader(value = "X-Internal-Ts", required = false) String internalTs,
            @RequestHeader(value = "X-Internal-Sig", required = false) String internalSig,
            @RequestHeader(value = "Authorization", required = false) String authz,
            @RequestBody AvatarUrlRequest req
    ) {
        Long memberId = resolveMemberId(internalUserId, internalTs, internalSig, authz);
        return ResponseEntity.ok(memberService.setAvatarUrl(memberId, req));
    }

    // === 인증 유틸 ===

    private Long resolveMemberId(String internalUserId, String ts, String sig, String authz) {
        // 1) 게이트웨이 내부 헤더 우선
        if (internalUserId != null && ts != null && sig != null) {
            verifyInternalSignature(internalUserId, ts, sig); // 유효하면 그대로 사용
            return Long.parseLong(internalUserId);
        }
        // 2) 없으면 JWT로 폴백(로컬/Swagger 직통 호출)
        if (authz == null || authz.isBlank()) {
            throw unauthorized("Missing internal headers and Authorization");
        }
        return jwt.parseAccessSubject(extractBearer(authz));
    }

    private void verifyInternalSignature(String memberId, String ts, String sigB64) {
        long now = java.time.Instant.now().getEpochSecond();
        long t = parseLongOrUnauthorized(ts, "Invalid X-Internal-Ts");
        // 재생공격 방지: 시계 드리프트 포함 60초 이내만 허용
        if (Math.abs(now - t) > 60) {
            throw unauthorized("X-Internal-Ts out of window");
        }
        byte[] expected = hmac(memberId + "|" + ts);
        byte[] actual = decodeBase64(sigB64);
        if (!java.security.MessageDigest.isEqual(expected, actual)) {
            throw unauthorized("Bad X-Internal-Sig");
        }
    }

    private static String extractBearer(String authz) {
        String token = authz.replaceFirst("(?i)^Bearer\\s+", "").trim();
        if (token.isEmpty()) throw unauthorized("Empty token");
        return token;
    }

    private static RuntimeException unauthorized(String msg) {
        return new org.springframework.web.server.ResponseStatusException(
                HttpStatus.UNAUTHORIZED, msg);
    }

    private long parseLongOrUnauthorized(String s, String msg) {
        try { return Long.parseLong(s); }
        catch (Exception e) { throw unauthorized(msg); }
    }

    private byte[] hmac(String msg) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    hmacSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(msg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC verify init failed", e);
        }
    }

    private static byte[] decodeBase64(String s) {
        return java.util.Base64.getDecoder().decode(s);
    }
}


