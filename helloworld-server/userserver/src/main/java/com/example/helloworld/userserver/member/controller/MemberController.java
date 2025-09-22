package com.example.helloworld.userserver.member.controller;

import com.example.helloworld.userserver.auth.jwt.JwtProvider;
import com.example.helloworld.userserver.member.dto.request.AvatarUrlRequest;
import com.example.helloworld.userserver.member.dto.request.MemberRegisterRequest;
import com.example.helloworld.userserver.member.dto.response.AvatarUrlResponse;
import com.example.helloworld.userserver.member.dto.response.MemberProfileResponse;
import com.example.helloworld.userserver.member.dto.response.MemberRegisterResponse;
import com.example.helloworld.userserver.member.service.MemberService;
import com.example.helloworld.userserver.member.util.InternalPrincipal;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Users", description = "회원 등록/조회/수정 API")
@RestController
@RequestMapping("api/users")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @PostMapping("/register")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MemberRegisterResponse> register(
            @AuthenticationPrincipal InternalPrincipal me,
            @Valid @RequestBody MemberRegisterRequest req
    ) {
        memberService.register(me.memberId(), req);
        return ResponseEntity.ok(MemberRegisterResponse.ok(me.memberId()));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MemberProfileResponse> getMe(
            @AuthenticationPrincipal InternalPrincipal me
    ) {
        return ResponseEntity.ok(memberService.getMe(me.memberId()));
    }

    @PutMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MemberRegisterResponse> updateMe(
            @AuthenticationPrincipal InternalPrincipal me,
            @Valid @RequestBody MemberProfileResponse.MemberUpdateRequest req
    ) {
        memberService.updateProfile(me.memberId(), req);
        return ResponseEntity.ok(MemberRegisterResponse.ok(me.memberId()));
    }

    @PutMapping("/profile-image")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<AvatarUrlResponse> putAvatarUrl(
            @AuthenticationPrincipal InternalPrincipal me,
            @RequestBody AvatarUrlRequest req
    ) {
        return ResponseEntity.ok(memberService.setAvatarUrl(me.memberId(), req));
    }
}



