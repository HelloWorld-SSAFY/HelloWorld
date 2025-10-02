package com.example.helloworld.weeklyserver.dto;

import java.util.List;

public record WeeklyDietsRes(
        int weekNo,
        int fromDayInWeek,
        int toDayInWeek,
        List<DietDayRes> days

) {
}
