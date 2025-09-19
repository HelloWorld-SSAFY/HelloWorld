package com.example.helloworld.calendar_diary_server.dto;

import java.time.LocalDate;
import java.util.List;

// 응답 전용 record
public record WeekResult(
        Long coupleId,
        int week,
        LocalDate startDate,
        LocalDate endDate,
        int count,
        List<DiaryResponse> items
) {}