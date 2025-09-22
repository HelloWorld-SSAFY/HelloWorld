package com.example.helloworld.userserver.alarm.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import com.example.helloworld.userserver.alarm.domain.AlarmType;

public record AlarmCreateRequest(
        @NotNull  AlarmType alarm_type,
        @NotBlank String    alarm_title,
        @NotBlank String    alarm_msg,
        @NotNull  Instant   created_at
) {}