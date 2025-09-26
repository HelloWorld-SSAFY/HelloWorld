package com.example.helloworld.userserver.alarm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LatestByPlatformResponse {
    private Long userId;
    private String platform;
    private String token;
}
