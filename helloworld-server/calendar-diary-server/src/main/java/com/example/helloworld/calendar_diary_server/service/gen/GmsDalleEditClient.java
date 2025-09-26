// service/gen/GmsDalleEditClient.java
package com.example.helloworld.calendar_diary_server.service.gen;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

@Slf4j
@Component
@RequiredArgsConstructor
public class GmsDalleEditClient {

    @Value("${app.gms.base-url}")
    private String baseUrl;
    @Value("${app.gms.api-key}")
    private String apiKey;
    @Value("${app.gms.size:1024x1024}")
    private String size;

    private RestClient client() {
        RequestConfig reqCfg = RequestConfig.custom()
                .setConnectTimeout(10, TimeUnit.SECONDS)
                .setResponseTimeout(180, TimeUnit.SECONDS)
                .build();

        CloseableHttpClient apache = HttpClients.custom()
                .setDefaultRequestConfig(reqCfg)
                .build();

        var rf = new HttpComponentsClientHttpRequestFactory(apache);

        return RestClient.builder()
                .requestFactory(rf)
                .baseUrl("https://gms.ssafy.io")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    public byte[] editImage(byte[] originalImage, String prompt) {
        try {
            // 1. 이미지를 정사각형 PNG로 변환
            byte[] processedImage = convertToSquarePng(originalImage);

            log.info("Editing image with prompt: {}", prompt);
            log.info("Original image size: {}, Processed size: {}", originalImage.length, processedImage.length);

            // 2. Multipart 요청 구성
            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();

            // 이미지 파일 추가
            ByteArrayResource imageResource = new ByteArrayResource(processedImage) {
                @Override
                public String getFilename() {
                    return "image.png";
                }
            };
            parts.add("image", imageResource);

            // 프롬프트 추가
            parts.add("prompt", prompt);
            parts.add("n", "1");
            parts.add("size", size);
            parts.add("response_format", "b64_json");

            // 3. API 호출
            GmsResponse resp = client().post()
                    .uri("/gmsapi/api.openai.com/v1/images/edits")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(GmsResponse.class);

            if (resp == null || resp.data == null || resp.data.isEmpty() || resp.data.get(0).b64 == null) {
                log.error("Empty or invalid response from GMS: {}", resp);
                throw new IllegalStateException("GMS image edit failed or empty response");
            }

            log.info("Successfully edited image, response size: {} items", resp.data.size());
            return Base64.getDecoder().decode(resp.data.get(0).b64);

        } catch (RestClientResponseException e) {
            String body = e.getResponseBodyAsString();
            log.error("GMS API Error - Status: {}, Body: {}", e.getStatusCode(), body);
            throw new IllegalStateException("GMS/OpenAI API Error. status=" + e.getStatusCode() + " body=" + body, e);
        } catch (Exception e) {
            log.error("Unexpected error during image editing", e);
            throw new IllegalStateException("Failed to edit image: " + e.getMessage(), e);
        }
    }

    private byte[] convertToSquarePng(byte[] imageBytes) throws IOException {
        // 이미지 읽기
        BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (originalImage == null) {
            throw new IllegalArgumentException("Invalid image format");
        }

        // 정사각형 크기 결정 (긴 쪽을 기준)
        int size = Math.max(originalImage.getWidth(), originalImage.getHeight());

        // 정사각형 캔버스 생성 (흰색 배경)
        BufferedImage squareImage = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = squareImage.createGraphics();

        // 흰색 배경 채우기
        g2d.setColor(java.awt.Color.WHITE);
        g2d.fillRect(0, 0, size, size);

        // 원본 이미지를 중앙에 배치
        int x = (size - originalImage.getWidth()) / 2;
        int y = (size - originalImage.getHeight()) / 2;
        g2d.drawImage(originalImage, x, y, null);
        g2d.dispose();

        // PNG로 변환
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(squareImage, "PNG", baos);

        byte[] result = baos.toByteArray();
        log.info("Converted image to {}x{} PNG, size: {} bytes", size, size, result.length);

        return result;
    }

    // Response 클래스들 (기존과 동일)
    static class GmsResponse {
        public List<Item> data;
    }

    static class Item {
        public String b64;
        public String url;
    }
}