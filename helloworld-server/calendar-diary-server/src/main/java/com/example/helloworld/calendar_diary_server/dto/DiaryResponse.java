package com.example.helloworld.calendar_diary_server.dto;

import com.example.helloworld.calendar_diary_server.entity.Diary;

import java.time.Instant;
import java.time.LocalDate;

public record DiaryResponse(
        Long id,
        Long coupleId,
        LocalDate targetDate,
        Instant createdAt,
        String title,
        String content
) {
    public static DiaryResponse from(Diary d) {
        return new DiaryResponse(d.getDiaryId(), d.getCoupleId(), d.getTargetDate(),
                d.getCreatedAt().toInstant(), d.getDiaryTitle(), d.getDiaryContent());
    }
}