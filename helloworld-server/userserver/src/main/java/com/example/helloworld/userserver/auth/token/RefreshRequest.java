package com.example.helloworld.userserver.auth.token;

public record RefreshRequest(
        String refreshToken
) {}
