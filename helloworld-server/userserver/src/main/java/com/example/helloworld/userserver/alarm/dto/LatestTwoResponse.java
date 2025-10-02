package com.example.helloworld.userserver.alarm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LatestTwoResponse {
    private Long userId;
    private String mobileToken; // MOBILE 최신 1개
    private String wearToken;   // WEAR_OS 최신 1개
}