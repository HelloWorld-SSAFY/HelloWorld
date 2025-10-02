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
        String content,
        Long authorId,
        String authorRole,
        String thumbnailUrl

) {
    public static DiaryResponse from(Diary d) {
        return new DiaryResponse(
                d.getDiaryId(),
                d.getCoupleId(),
                d.getTargetDate(),
                d.getCreatedAt().toInstant(),
                d.getDiaryTitle(),
                d.getDiaryContent(),
                d.getAuthorId(),
                d.getAuthorRole().name(),
                null
        );
    }

    public static DiaryResponse from(Diary d, String thumbnailUrl) {
        return new DiaryResponse(d.getDiaryId(), d.getCoupleId(), d.getTargetDate(),
                d.getCreatedAt().toInstant(), d.getDiaryTitle(), d.getDiaryContent(),
                d.getAuthorId(), // 작성자 ID 추가
                d.getAuthorRole().name(),thumbnailUrl);
    }
}