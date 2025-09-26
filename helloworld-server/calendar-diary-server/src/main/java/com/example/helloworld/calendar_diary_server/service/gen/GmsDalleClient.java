// service/gen/GmsDalleClient.java
package com.example.helloworld.calendar_diary_server.service.gen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
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
                .setExpectContinueEnabled(false)
                .build();

        CloseableHttpClient apache = HttpClients.custom()
                .setDefaultRequestConfig(reqCfg)
                .disableContentCompression() // gzip 비활성화
                .build();

        // 2) Buffering 래퍼 제거 → 바로 Apache factory 사용
        var rf = new HttpComponentsClientHttpRequestFactory(apache);

        // 3) RestClient
        return RestClient.builder()
                .requestFactory(rf)
                .baseUrl("https://gms.ssafy.io")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.ACCEPT, "*/*")
                .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "identity")
                .defaultHeader(HttpHeaders.USER_AGENT, "curl/8.6.0")
                .defaultHeader(HttpHeaders.CONNECTION, "close") // (임시) keep-alive 억제
                .build();
    }


    @Override
    public byte[] generateCaricature(byte[] ignored) {
        throw new UnsupportedOperationException("Use generateCaricatureWithPrompt(prompt)");
    }

    public byte[] generateCaricatureWithPrompt(String prompt) {
        Map<String, Object> payload = Map.of(
                "model", model,
                "prompt", prompt,
                "size", size,
                "n", 1,
                "response_format", "b64_json"
        );

        try {
            GmsResponse resp = client().post()
                    .uri("/gmsapi/api.openai.com/v1/images/generations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(GmsResponse.class);

            if (resp == null || resp.data == null || resp.data.isEmpty() || resp.data.get(0).b64 == null) {
                throw new IllegalStateException("GMS image generation failed or empty response");
            }
            return Base64.getDecoder().decode(resp.data.get(0).b64);

        } catch (RestClientResponseException e) {
            String body = e.getResponseBodyAsString();
            throw new IllegalStateException("GMS/OpenAI 4xx/5xx. status=" + e.getStatusCode() + " body=" + body, e);
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
