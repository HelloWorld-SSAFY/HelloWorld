package com.example.helloworld.auth.controller;

import com.example.helloworld.auth.application.AuthService;
import com.example.helloworld.auth.application.command.LoginCommand;
import com.example.helloworld.auth.application.result.LoginResult;
import com.example.helloworld.auth.presentation.request.LoginRequest;
import com.example.helloworld.auth.presentation.request.LogoutRequest;
import com.example.helloworld.auth.presentation.response.LoginResponse;
import com.example.helloworld.auth.token.RefreshRequest;
import com.example.helloworld.auth.token.RefreshResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthController {
    private final AuthService authService;

    @PostMapping("/google")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginCommand command = request.toCommand();
        LoginResult result = authService.login(command);
        return ResponseEntity.ok(LoginResponse.from(result));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(@RequestBody RefreshRequest req){
        return ResponseEntity.ok(authService.refresh(req));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody LogoutRequest req) {
        authService.logout(req);
        return ResponseEntity.noContent().build(); // 204
    }

}
