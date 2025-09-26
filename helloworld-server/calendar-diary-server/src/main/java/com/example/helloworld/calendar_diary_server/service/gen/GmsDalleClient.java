// service/gen/GmsDalleClient.java
package com.example.helloworld.calendar_diary_server.service.gen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class GmsDalleClient implements GmsImageGenClient {

    @Value("${app.gms.base-url}")   // e.g. https://gms.ssafy.io/gmsapi  (끝에 / 없음)
    private String baseUrl;
    @Value("${app.gms.api-key}")
    private String apiKey;
    @Value("${app.gms.model:dall-e-3}")
    private String model;
    @Value("${app.gms.size:1024x1024}")
    private String size;

    private RestClient client() {
        // 1) Apache 클라이언트 옵션 (전송 안정화)
        RequestConfig reqCfg = RequestConfig.custom()
                .setConnectTimeout(10, TimeUnit.SECONDS)
                .setResponseTimeout(180, TimeUnit.SECONDS)
                .build();

        CloseableHttpClient apache = HttpClients.custom()
                .setDefaultRequestConfig(reqCfg)
                .build();

        // 2) Buffering 래퍼 제거 → 바로 Apache factory 사용
        var rf = new HttpComponentsClientHttpRequestFactory(apache);

        // 3) RestClient
        return RestClient.builder()
                .requestFactory(rf)
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    @Override
    public byte[] generateCaricature(byte[] ignored) {
        throw new UnsupportedOperationException("Use generateCaricatureWithPrompt(prompt)");
    }

    public byte[] generateCaricatureWithPrompt(String prompt) {
        // 프롬프트 검증 및 정제
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt cannot be null or empty");
        }

        // 프롬프트 길이 제한 (DALL-E 3는 4000자 제한)
        if (prompt.length() > 4000) {
            log.warn("Prompt too long ({}), truncating to 4000 chars", prompt.length());
            prompt = prompt.substring(0, 4000);
        }

        log.info("Generating image with prompt: {}", prompt);
        log.info("Using model: {}, size: {}", model, size);

        Map<String, Object> payload = Map.of(
                "model", model,
                "prompt", prompt.trim(),
                "size", size,
                "response_format", "b64_json"
        );

        // 요청 페이로드 로깅
        log.debug("Request payload: {}", payload);

        try {
            GmsResponse resp = client().post()
                    .uri("/api.openai.com/v1/images/generations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(GmsResponse.class);

            if (resp == null || resp.data == null || resp.data.isEmpty() || resp.data.get(0).b64 == null) {
                log.error("Empty or invalid response from GMS: {}", resp);
                throw new IllegalStateException("GMS image generation failed or empty response");
            }

            log.info("Successfully generated image, response size: {} items", resp.data.size());
            return Base64.getDecoder().decode(resp.data.get(0).b64);

        } catch (RestClientResponseException e) {
            String body = e.getResponseBodyAsString();

            // 상세한 에러 로깅
            log.error("GMS API Error - Status: {}, Headers: {}", e.getStatusCode(), e.getResponseHeaders());
            log.error("GMS API Error - Response Body: {}", body);
            log.error("Request details - Model: {}, Size: {}, Prompt length: {}", model, size, prompt.length());

            // 특정 에러 케이스 처리
            if (e.getStatusCode().value() == 400) {
                if (body.contains("cloudflare")) {
                    throw new IllegalStateException("Request blocked by Cloudflare. Check request format and content.", e);
                } else if (body.contains("content_policy_violation")) {
                    throw new IllegalStateException("Content policy violation. Please modify your prompt.", e);
                } else if (body.contains("invalid_request")) {
                    throw new IllegalStateException("Invalid request format. Check API parameters.", e);
                }
            }

            throw new IllegalStateException("GMS/OpenAI API Error. status=" + e.getStatusCode() + " body=" + body, e);
        } catch (Exception e) {
            log.error("Unexpected error during image generation", e);
            throw new IllegalStateException("Failed to generate image: " + e.getMessage(), e);
        }
    }

@JsonIgnoreProperties(ignoreUnknown = true)
static class GmsResponse { public List<Item> data; }

@JsonIgnoreProperties(ignoreUnknown = true)
static class Item {
    @JsonProperty("b64_json")
    public String b64;
    public String url;
}
}