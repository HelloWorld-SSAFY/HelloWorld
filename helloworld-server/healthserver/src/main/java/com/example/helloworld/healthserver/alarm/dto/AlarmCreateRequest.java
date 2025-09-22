package com.example.helloworld.healthserver.alarm.dto;

import com.example.helloworld.healthserver.alarm.domain.AlarmType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record AlarmCreateRequest(
        @NotNull  AlarmType alarm_type,
        @NotBlank String    alarm_title,
        @NotBlank String    alarm_msg,
        @NotNull  Instant   created_at
) {}