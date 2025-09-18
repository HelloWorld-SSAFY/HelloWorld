package com.example.helloworld.calendar_diary_server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

// 생성 요청
public record DiaryCreateRequest(
        Long coupleId,
        LocalDate targetDate,
        String title,
        String content
) {}
