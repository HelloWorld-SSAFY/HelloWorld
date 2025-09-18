package com.example.helloworld.calendar_diary_server.dto;

import java.time.LocalDate;
import java.util.List;

public record DayResult(
        Long coupleId,
        int day,
        int week,
        LocalDate date,
        int count,
        List<DiaryResponse> items
) {}