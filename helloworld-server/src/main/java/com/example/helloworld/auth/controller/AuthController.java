package com.example.helloworld.auth.controller;

import com.example.helloworld.auth.application.AuthService;
import com.example.helloworld.auth.application.command.LoginCommand;
import com.example.helloworld.auth.application.result.LoginResult;
import com.example.helloworld.auth.jwt.JwtProvider;
import com.example.helloworld.auth.presentation.request.LoginRequest;
import com.example.helloworld.auth.presentation.request.LogoutRequest;
import com.example.helloworld.auth.presentation.response.LoginResponse;
import com.example.helloworld.auth.token.RefreshRequest;
import com.example.helloworld.auth.token.RefreshResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;


@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthController {
    private final AuthService authService;
    private final JwtProvider jwtProvider;

    @PostMapping("/google")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginCommand command = request.toCommand();
        LoginResult result = authService.login(command);
        return ResponseEntity.ok(LoginResponse.from(result));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(@RequestBody RefreshRequest req) {
        return ResponseEntity.ok(authService.refresh(req));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody LogoutRequest req) {
        authService.logout(req);
        return ResponseEntity.noContent().build(); // 204
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Void> delete(@RequestHeader("Authorization") String authz) {
        String token = extractBearer(authz);          // "Bearer xxx" → "xxx"
        Long memberId = jwtProvider.parseAccessSubject(token);
        authService.withdraw(memberId);
        return ResponseEntity.noContent().build();    // 204
    }

    private static String extractBearer(String authz) {
        if (authz == null || !authz.startsWith("Bearer ")) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return authz.substring(7).trim();
    }

}
