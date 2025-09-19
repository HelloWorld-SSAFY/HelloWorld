package com.example.helloworld.healthserver.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public final class StepsDtos {

    // 등록 요청
    public record CreateRequest(
            Instant date,     // 측정 시점(UTC Instant). 없으면 서비스에서 now()로 보정 가능
            Integer steps     // 걸음수(분/시간/일 단위 중 선택한 기준에 맞춰 전송)
    ) {}

    // 등록 응답
    public record CreateResponse(
            @JsonProperty("steps_id") Long stepsId,
            Instant date,
            Integer steps
    ) {}
}