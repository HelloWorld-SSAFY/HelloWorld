// service/gen/GmsDalleClient.java
package com.example.helloworld.calendar_diary_server.service.gen;

import com.example.helloworld.calendar_diary_server.logging.HttpWireLogging;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.util.Base64;
import java.util.List;
import java.util.Map;

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
        var jdk = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1) // ★ HTTP/1.1 강제
                .build();

        var rf = new BufferingClientHttpRequestFactory(new JdkClientHttpRequestFactory(jdk));
        return RestClient.builder()
                .requestFactory(rf)
                .baseUrl("https://gms.ssafy.io")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Accept", "*/*")
                .defaultHeader("Accept-Encoding", "identity")
                .defaultHeader("Connection", "close") // (임시) 커넥션 재사용 억제
                .defaultHeader("User-Agent", "curl/8.6.0")
                .build();
    }


    @Override
    public byte[] generateCaricature(byte[] ignored) {
        throw new UnsupportedOperationException("Use generateCaricatureWithPrompt(prompt)");
    }

    public byte[] generateCaricatureWithPrompt(String prompt) {
        Map<String, Object> payload = Map.of(
                "model", model,                 // dall-e-3 또는 gpt-image-1
                "prompt", prompt,
                "size", size,
                "n", 1,
                "response_format", "b64_json"
        );

        try {
            GmsResponse resp = client().post()
                    .uri("/gmsapi/api.openai.com/v1/images/generations")  //풀 경로
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(GmsResponse.class);


            if (resp == null || resp.data == null || resp.data.isEmpty() || resp.data.get(0).b64 == null) {
                throw new IllegalStateException("GMS image generation failed or empty response");
            }
            return Base64.getDecoder().decode(resp.data.get(0).b64);

        } catch (RestClientResponseException e) {
            // ⬇원문 바디를 그대로 남겨야 원인 파악 가능
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
