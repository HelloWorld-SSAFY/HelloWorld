package com.example.helloworld.userserver.auth.application.result;

public record LoginResult(
        Long memberId,
        String accessToken,
        String refreshToken
) {
}

