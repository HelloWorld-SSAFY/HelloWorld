package com.example.helloworld.weeklyserver.dto;

import java.util.List;

public record WeeklyWorkoutsRes(int weekNo, List<WorkoutItemRes> items) {
}
