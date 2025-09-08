package com.example.helloworld.auth.token;

public record RefreshResponse(
        Long memberId,
        String accessToken,
        String refreshToken
) {}