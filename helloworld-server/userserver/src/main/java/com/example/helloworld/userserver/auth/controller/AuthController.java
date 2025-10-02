package com.example.helloworld.userserver.auth.controller;

import com.example.helloworld.userserver.auth.application.AuthService;
import com.example.helloworld.userserver.auth.application.command.LoginCommand;
import com.example.helloworld.userserver.auth.application.result.LoginResult;
import com.example.helloworld.userserver.auth.jwt.JwtProvider;
import com.example.helloworld.userserver.auth.presentation.request.LoginRequest;
import com.example.helloworld.userserver.auth.presentation.request.LogoutRequest;
import com.example.helloworld.userserver.auth.presentation.response.LoginResponse;
import com.example.helloworld.userserver.auth.token.RefreshRequest;
import com.example.helloworld.userserver.auth.token.RefreshResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "인증/인가 API")
@RestController
@RequestMapping("api/auth")
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthController {

    private final AuthService authService;
    private final JwtProvider jwtProvider;

    @Operation(
            summary = "구글 로그인",
            description = "구글 ID 토큰으로 로그인하고 Access/Refresh 토큰을 발급합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType="application/json",
                            schema = @Schema(implementation = LoginRequest.class),
                            examples = @ExampleObject(
                                    name = "예시",
                                    value = """
                                      {
                                        "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6..."
                                      }
                                      """
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "accessToken": "at.jwt.token",
                                              "refreshToken": "rt.jwt.token",
                                              "gender" : null
                                            }
                                            """
                            ))),
            @ApiResponse(responseCode = "401", description = "ID 토큰 검증 실패"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/google")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginCommand command = request.toCommand();
        LoginResult result = authService.login(command);
        return ResponseEntity.ok(LoginResponse.from(result));
    }

    @Operation(
            summary = "토큰 재발급",
            description = "Refresh 토큰으로 Access/Refresh 토큰을 재발급합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = RefreshRequest.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "refreshToken": "rt.jwt.token"
                                            }
                                            """
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재발급 성공",
                    content = @Content(schema = @Schema(implementation = RefreshResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "accessToken": "new.at.jwt.token",
                                              "refreshToken": "new.rt.jwt.token"
                                            }
                                            """
                            ))),
            @ApiResponse(responseCode = "401", description = "Refresh 토큰 만료/위조"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(@RequestBody RefreshRequest req) {
        return ResponseEntity.ok(authService.refresh(req));
    }

    @Operation(
            summary = "로그아웃",
            description = "서버 저장소의 Refresh 토큰을 폐기합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = LogoutRequest.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "refreshToken": "rt.jwt.token"
                                            }
                                            """
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "로그아웃 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authz,
            @RequestBody LogoutRequest req
    ) {
        String at = authz != null && authz.startsWith("Bearer ") ? authz.substring(7) : null;
        authService.logout(req, at);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "회원 탈퇴",
            description = "Authorization 헤더의 Access 토큰으로 본인 확인 후 회원 탈퇴(하드 삭제)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "탈퇴 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패(Authorization 헤더 누락/형식 오류)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @DeleteMapping("/delete")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Bearer {accessToken} 형식", required = true,
                    examples = @ExampleObject(name = "Authorization", value = "Bearer at.jwt.token"))
            @RequestHeader("Authorization") String authz) {

        String token = extractBearer(authz);          // "Bearer xxx" → "xxx"
        Long memberId = jwtProvider.parseAccessSubject(token);
        authService.withdraw(memberId);
        return ResponseEntity.noContent().build();
    }

    private static String extractBearer(String authz) {
        if (authz == null || !authz.startsWith("Bearer ")) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Authorization header");
        }
        return authz.substring(7).trim();
    }
}
