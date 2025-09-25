// service/gen/GmsDalleClient.java
package com.example.helloworld.calendar_diary_server.service.gen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GmsDalleClient implements GmsImageGenClient {

    @Value("${app.gms.base-url}")
    private String baseUrl;
    @Value("${app.gms.api-key}")
    private String apiKey;
    @Value("${app.gms.model:dall-e-3}")
    private String model;
    @Value("${app.gms.size:1024x1024}")
    private String size;

    private RestClient client() {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
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
                "response_format", "b64_json"
        );

        GmsResponse resp = client().post()
                .uri("/api.openai.com/v1/images/generations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(GmsResponse.class);

        if (resp == null || resp.data == null || resp.data.isEmpty() || resp.data.get(0).b64 == null) {
            throw new IllegalStateException("GMS image generation failed or empty response");
        }
        return Base64.getDecoder().decode(resp.data.get(0).b64);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class GmsResponse {
        public List<Item> data;
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Item {
        @JsonProperty("b64_json")
        public String b64;
        public String url;
    }
}
