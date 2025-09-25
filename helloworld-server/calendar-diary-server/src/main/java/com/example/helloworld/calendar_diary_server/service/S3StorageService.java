package com.example.helloworld.calendar_diary_server.service;

import com.example.helloworld.calendar_diary_server.config.S3Config;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

// service/S3StorageService.java
// service/S3StorageService.java
@Service
@RequiredArgsConstructor
public class S3StorageService {
    private final S3Client s3;
    private final S3Presigner presigner;
    private final S3Config cfg;

    public static record UploadResult(String key, String url) {}

    public UploadResult upload(String categoryKey, MultipartFile file) throws IOException {
        String prefix = cfg.getPath().getOrDefault(categoryKey, categoryKey);
        String ext = ext(file.getOriginalFilename());
        String key = "%s/%s/%s.%s".formatted(prefix, LocalDate.now(), UUID.randomUUID(), ext);

        // 여기: MIME 타입 보정 + inline 표시
        String mime = resolveMime(file.getOriginalFilename(), file.getContentType());

        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(cfg.getBucket())
                .key(key)
                .contentType(mime)                 // ← image/jpeg 등으로 강제
                .contentDisposition("inline")      // ← 브라우저에서 바로 표시 유도
                .build();

        try (InputStream in = file.getInputStream()) {
            s3.putObject(put, RequestBody.fromInputStream(in, file.getSize()));
        }

        // presigned GET (10분 유효) – 필요 시 여기서도 override 가능
        var getReq = GetObjectRequest.builder()
                .bucket(cfg.getBucket())
                .key(key)
                 .responseContentType(mime)        // (선택) 응답 헤더 강제
                 .responseContentDisposition("inline")
                .build();

        var pre = presigner.presignGetObject(b -> b
                .signatureDuration(Duration.ofMinutes(10))
                .getObjectRequest(getReq));

        return new UploadResult(key, pre.url().toString());
    }

    private static String resolveMime(String filename, String declared) {
        // 클라이언트가 제대로 보내줬으면 그대로 사용
        if (declared != null) {
            String low = declared.toLowerCase();
            if (!low.isBlank() && !low.equals("application/octet-stream")) return low;
        }
        // 확장자로 보정
        String ext = ext(filename).toLowerCase();
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            case "svg" -> "image/svg+xml";
            case "heic", "heif" -> "image/heic"; // 브라우저 미표시 가능
            default -> "application/octet-stream";
        };
    }

    private static String ext(String name) {
        if (name == null || !name.contains(".")) return "bin";
        return name.substring(name.lastIndexOf('.') + 1);
    }

    // service/S3StorageService.java  (추가 메소드)
    public UploadResult uploadBytes(String categoryKey, String filename, String contentType, byte[] bytes) {
        String prefix = cfg.getPath().getOrDefault(categoryKey, categoryKey);
        String ext = ext(filename);
        String key = "%s/%s/%s.%s".formatted(prefix, LocalDate.now(), UUID.randomUUID(), ext);

        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(cfg.getBucket())
                .key(key)
                .contentType(contentType != null ? contentType : "application/octet-stream")
                .contentDisposition("inline")
                .build();

        s3.putObject(put, RequestBody.fromBytes(bytes));

        var getReq = GetObjectRequest.builder()
                .bucket(cfg.getBucket())
                .key(key)
                .responseContentType(contentType)
                .responseContentDisposition("inline")
                .build();

        var pre = presigner.presignGetObject(b -> b
                .signatureDuration(Duration.ofMinutes(10))
                .getObjectRequest(getReq));

        return new UploadResult(key, pre.url().toString());
    }

    public byte[] download(String key) throws IOException {
        var obj = s3.getObject(GetObjectRequest.builder()
                .bucket(cfg.getBucket()).key(key).build());
        return obj.readAllBytes();
    }

}


