package com.example.helloworld.weeklyserver.service;

import com.example.helloworld.weeklyserver.config.S3Config;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class S3UrlService {
    private final S3Presigner presigner;
    private final S3Config s3cfg;

    public String presignGet(String rawKey, Duration ttl) {
        String key = normalizeKeyFromDb(rawKey);       // ① 형식 통일
        key = applyPathAlias(key, s3cfg.getPath());    // ② ${alias} → 실제 prefix 치환

        if (key == null || key.isBlank()) return null;

        var getReq = GetObjectRequest.builder()
                .bucket(s3cfg.getBucket())
                .key(key)
                .build();

        var preReq = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(getReq)
                .build();

        // SDK가 한글 경로는 자동으로 퍼센트 인코딩해줌
        return presigner.presignGetObject(preReq).url().toString();
    }

    /** DB에서 읽은 값이 다음 중 아무 형식이어도 key만 추출:
     *  - "food/갈치.jpg"
     *  - "/food/갈치.jpg"
     *  - "hellowolrd-s3/food/갈치.jpg"
     *  - "s3://hellowolrd-s3/food/갈치.jpg"
     *  - "${foods}/갈치.jpg" (별칭)
     */
    private String normalizeKeyFromDb(String v) {
        if (v == null) return null;
        String s = v.trim();
        if (s.isEmpty()) return s;

        // s3://bucket/key → key
        if (s.startsWith("s3://")) {
            int firstSlash = s.indexOf('/', "s3://".length());
            if (firstSlash > 0 && firstSlash + 1 < s.length()) {
                String bucket = s.substring("s3://".length(), firstSlash);
                // 버킷명이 설정과 다르면(오타 등) presign 시 404 가능 → 로그로 확인
                if (!bucket.equals(s3cfg.getBucket())) {
                    // 필요시 warn 로그 남기기
                }
                return s.substring(firstSlash + 1);
            }
            return "";
        }

        // "bucket/key" → key
        String bucketPrefix = s3cfg.getBucket() + "/";
        if (s.startsWith(bucketPrefix)) {
            return s.substring(bucketPrefix.length());
        }

        // "/key" → key
        if (s.startsWith("/")) return s.substring(1);

        // 나머지는 그대로(key로 간주). 예: "food/갈치.jpg" 또는 "${foods}/갈치.jpg"
        return s;
    }

    /** ${alias} 치환: app.s3.path에 등록한 매핑으로 바꿔줌 */
    private static String applyPathAlias(String key, Map<String, String> alias) {
        if (key == null) return null;
        if (alias == null || alias.isEmpty()) return key;
        String out = key;
        for (var e : alias.entrySet()) {
            out = out.replace("${" + e.getKey() + "}", e.getValue());
        }
        return out;
    }
}
