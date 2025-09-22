package com.example.helloworld.healthserver.alarm.service;

import com.example.helloworld.healthserver.alarm.dto.AiResponse;
import com.example.helloworld.healthserver.alarm.dto.AiTelemetryRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AiClient {

    private final RestClient restClient;

    private final String telemetryPath;

    public AiClient(RestClient restClient,
                    @Value("${ai.telemetry-path:/api/health/telemetry}") String telemetryPath) {
        this.restClient = restClient;
        this.telemetryPath = telemetryPath;
    }

    public AiResponse postTelemetry(AiTelemetryRequest body) {
        try {
            return restClient.post()
                    .uri(telemetryPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(AiResponse.class);
        } catch (RestClientResponseException e) {
            // 4xx/5xx 응답 본문 로깅 등
            AiResponse fallback = new AiResponse();
            fallback.setOk(false);
            fallback.setMode("normal");
            return fallback;
        }
    }
}