package com.example.helloworld.auth.presentation.response;


import com.example.helloworld.auth.application.result.LoginResult;

public record LoginResponse(
        Long memberId,
        String accessToken,
        String refreshToken
) {
    public static LoginResponse from(LoginResult result){
        return new LoginResponse(
                result.memberId(),
                result.accessToken(),
                result.refreshToken()
        );
    }
}

