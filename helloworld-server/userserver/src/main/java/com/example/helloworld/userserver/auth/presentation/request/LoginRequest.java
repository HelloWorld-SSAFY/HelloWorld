package com.example.helloworld.userserver.auth.presentation.request;


import com.example.helloworld.userserver.auth.application.command.LoginCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest", description = "구글 ID 토큰 로그인 요청")
public record LoginRequest(
        @Schema(description = "구글 ID 토큰", example = "eyJhbGciOiJSUzI1NiIsImtpZCI6...", required = true)
        @NotBlank
        String idToken
) {
    public LoginCommand toCommand(){
        return new LoginCommand(idToken);
    }
}
