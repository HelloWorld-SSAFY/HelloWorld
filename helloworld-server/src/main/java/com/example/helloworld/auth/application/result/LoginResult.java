package com.example.helloworld.auth.application.result;

public record LoginResult(
        Long memberId,
        String accessToken,
        String refreshToken
) {
}

