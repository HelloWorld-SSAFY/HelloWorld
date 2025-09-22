package com.example.helloworld.healthserver.alarm.dto;

import java.util.Map;

public record AiTelemetryRequest(
        String user_ref,     // 예: "c12345"
        String ts,           // ISO8601 (+09:00 권장)
        Map<String, Object> metrics  // { "hr":150, "stress":0.72 }
) {}