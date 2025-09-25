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
@Service
@RequiredArgsConstructor
public class S3StorageService {
    private final S3Client s3;
    private final S3Presigner presigner;
    private final S3Config cfg;

    public static record UploadResult(String key, String url) {}

    public UploadResult upload(String categoryKey, MultipartFile file) throws IOException {
        String prefix = cfg.getPath().getOrDefault(categoryKey, categoryKey); // "caricature"
        String ext = ext(file.getOriginalFilename());
        String key = "%s/%s/%s.%s".formatted(prefix, LocalDate.now(), UUID.randomUUID(), ext);

        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(cfg.getBucket())
                .key(key)
                .contentType(Optional.ofNullable(file.getContentType()).orElse("application/octet-stream"))
                .build();

        try (InputStream in = file.getInputStream()) {
            s3.putObject(put, RequestBody.fromInputStream(in, file.getSize()));
        }

        // presigned GET (10분 유효)
        var getReq = GetObjectRequest.builder().bucket(cfg.getBucket()).key(key).build();
        var pre = presigner.presignGetObject(b -> b.signatureDuration(Duration.ofMinutes(10))
                .getObjectRequest(getReq));

        return new UploadResult(key, pre.url().toString());  // key + 임시 URL 반환
    }

    private static String ext(String name) {
        if (name == null || !name.contains(".")) return "bin";
        return name.substring(name.lastIndexOf('.') + 1);
    }
}

