package com.example.helloworld.userserver.member.controller;

import com.example.helloworld.userserver.auth.jwt.JwtProvider;
import com.example.helloworld.userserver.member.dto.request.CoupleJoinRequest;
import com.example.helloworld.userserver.member.dto.response.CoupleJoinResponse;
import com.example.helloworld.userserver.member.dto.response.CoupleUnlinkResponse;
import com.example.helloworld.userserver.member.dto.response.InviteCodeIssueResponse;
import com.example.helloworld.userserver.member.service.CoupleInviteService;
import com.example.helloworld.userserver.member.util.InternalPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@Tag(name = "Couple Invites", description = "초대코드 발급/합류 API")
@RestController
@RequestMapping("/api/couples")
@RequiredArgsConstructor
public class CoupleInviteController {

    private final CoupleInviteService inviteService;

    @PostMapping("/invite")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<InviteCodeIssueResponse> issue(
            @AuthenticationPrincipal InternalPrincipal me
    ) {
        return ResponseEntity.ok(inviteService.issue(me.memberId()));
    }

    @PostMapping("/join")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CoupleJoinResponse> join(
            @AuthenticationPrincipal InternalPrincipal me,
            @Valid @RequestBody CoupleJoinRequest req
    ) {
        return ResponseEntity.ok(inviteService.join(me.memberId(), req));
    }

    @DeleteMapping("/divorce")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CoupleUnlinkResponse> unlink(
            @AuthenticationPrincipal InternalPrincipal me
    ) {
        return ResponseEntity.ok(inviteService.unlink(me.memberId()));
    }
}