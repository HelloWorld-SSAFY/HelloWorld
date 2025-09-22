package com.example.helloworld.userserver.member.util;

public record InternalPrincipal(
        Long memberId,
        Long coupleId,
        String role,
        String tokenHash // 필요 없으면 null 가능
) {}

