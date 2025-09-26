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
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    private static final int MAX_LOG_BODY = 2048; // 2KB

    private RestClient client() {
        // 1) Apache 클라이언트 옵션
        RequestConfig reqCfg = RequestConfig.custom()
                .setConnectTimeout(10, TimeUnit.SECONDS)
                .setResponseTimeout(180, TimeUnit.SECONDS)
                .build();

        CloseableHttpClient apache = HttpClients.custom()
                .setDefaultRequestConfig(reqCfg)
                .build();

        // 2) 요청/응답 바디를 여러 번 읽기 위해 Buffering 팩토리 사용
        ClientHttpRequestFactory base = new HttpComponentsClientHttpRequestFactory(apache);
        BufferingClientHttpRequestFactory buffering = new BufferingClientHttpRequestFactory(base);

        // 3) RestClient + 로깅 인터셉터
        return RestClient.builder()
                .requestFactory(buffering)
                .baseUrl(baseUrl)
                .requestInterceptor(loggingInterceptor())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    private ClientHttpRequestInterceptor loggingInterceptor() {
        return (request, body, execution) -> {
            // --- 요청 로그 ---
            log.info(">>> [{}] {}", request.getMethod(), request.getURI());
            request.getHeaders().forEach((k, v) -> log.info(">>> {}: {}", k, redactHeader(k, v)));
            if (body != null && body.length > 0) {
                String bodyStr = truncate(new String(body, StandardCharsets.UTF_8));
                log.info(">>> body: {}", bodyStr);
            }

            // 실행
            ClientHttpResponse response = null;
            try {
                response = execution.execute(request, body);
            } catch (RestClientResponseException e) {
                // 예외형 응답도 이미 상위에서 잡지만, 여기서도 원시 상태를 기록
                log.warn("xxx RestClientResponseException during execution: status={} {}", e.getStatusCode(), e.getResponseBodyAsString());
                throw e;
            }

            // --- 응답 로그 ---
            try {
                byte[] respBytes = StreamUtils.copyToByteArray(response.getBody());
                String respBodyStr = truncate(new String(respBytes, StandardCharsets.UTF_8));

                log.info("<<< {} {}", response.getStatusCode().value(), response.getStatusText());
                response.getHeaders().forEach((k, v) -> log.info("<<< {}: {}", k, v));
                if (!respBodyStr.isEmpty()) {
                    log.info("<<< body: {}", respBodyStr);
                }

                // 응답 바디를 다시 읽을 수 있게 래핑해서 반환
                return new ReReadableClientHttpResponse(response, respBytes);
            } catch (IOException io) {
                log.warn("<<< (failed to read response body) {}", io.toString());
                return response; // 바디 로그만 포기
            }
        };
    }

    // Authorization 등 민감정보 마스킹
    private List<String> redactHeader(String key, List<String> values) {
        if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(key)) {
            return List.of("Bearer ***");
        }
        return values;
    }

    private String truncate(String s) {
        if (s == null) return "";
        if (s.length() <= MAX_LOG_BODY) return s;
        return s.substring(0, MAX_LOG_BODY) + "...(truncated)";
    }

    // 응답을 재사용 가능하게 만드는 래퍼
    static class ReReadableClientHttpResponse implements ClientHttpResponse {
        private final ClientHttpResponse delegate;
        private final byte[] body;

        ReReadableClientHttpResponse(ClientHttpResponse delegate, byte[] body) {
            this.delegate = delegate;
            this.body = body != null ? body : new byte[0];
        }

        @Override public org.springframework.http.HttpStatusCode getStatusCode() throws IOException { return delegate.getStatusCode(); }
        @Override public String getStatusText() throws IOException { return delegate.getStatusText(); }
        @Override public void close() { delegate.close(); }
        @Override public HttpHeaders getHeaders() { return delegate.getHeaders(); }
        @Override public java.io.InputStream getBody() { return new ByteArrayInputStream(body); }
    }

    @Override
    public byte[] generateCaricature(byte[] ignored) {
        throw new UnsupportedOperationException("Use generateCaricatureWithPrompt(prompt)");
    }

    public byte[] generateCaricatureWithPrompt(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt cannot be null or empty");
        }
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

        log.debug("Request payload(map): {}", payload);

        try {
            GmsResponse resp = client().post()
                    .uri("/api.openai.com/v1/images/generations") // << 요청하신 대로 그대로 둠
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
            log.error("GMS API Error - Status: {}, Headers: {}", e.getStatusCode(), e.getResponseHeaders());
            log.error("GMS API Error - Response Body: {}", body);
            log.error("Request details - Model: {}, Size: {}, Prompt length: {}", model, size, prompt.length());

            if (e.getStatusCode().value() == 400) {
                if (body != null && body.contains("cloudflare")) {
                    throw new IllegalStateException("Request blocked by Cloudflare. Check request format and content.", e);
                } else if (body != null && body.contains("content_policy_violation")) {
                    throw new IllegalStateException("Content policy violation. Please modify your prompt.", e);
                } else if (body != null && body.contains("invalid_request")) {
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
