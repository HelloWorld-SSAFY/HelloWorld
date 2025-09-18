package com.example.helloworld.healthserver.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(name="FetalMovementStatsResponse")
public record FmStatsResponse(
        List<Item> daily
) {
    public record Item(
            @JsonProperty("date") LocalDate date,
            @JsonProperty("count") long count
    ) {}
}