package com.example.helloworld.weeklyserver.dto;

public record WorkoutItemRes(
        WorkoutType type,
        String text,
        String title,
        String url,
        String thumbnailUrl,
        Integer orderNo
) {

}
