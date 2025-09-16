package com.example.helloworld.userserver.auth.token;

public record RefreshResponse(
        Long memberId,
        String accessToken,
        String refreshToken
) {}