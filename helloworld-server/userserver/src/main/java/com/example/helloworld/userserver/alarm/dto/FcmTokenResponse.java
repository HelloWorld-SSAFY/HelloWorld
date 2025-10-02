package com.example.helloworld.userserver.alarm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FcmTokenResponse {
    private Long userId;
    private String token;       // 최신 활성 토큰
}

