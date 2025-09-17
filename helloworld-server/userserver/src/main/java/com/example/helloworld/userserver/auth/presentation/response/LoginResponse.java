package com.example.helloworld.userserver.auth.presentation.response;

import com.example.helloworld.userserver.auth.application.result.LoginResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "LoginResponse", description = "로그인 결과")
public record LoginResponse(
        @Schema(description = "회원 ID", example = "123")
        Long memberId,
        @Schema(description = "액세스 토큰", example = "at.jwt.token")
        String accessToken,
        @Schema(description = "리프레시 토큰", example = "rt.jwt.token")
        String refreshToken,
        @Schema(description = "성별", example = "null")
                String gender
) {
    public static LoginResponse from(LoginResult result){
        return new LoginResponse(
                result.memberId(),
                result.accessToken(),
                result.refreshToken(),
                result.gender()
        );
    }
}

