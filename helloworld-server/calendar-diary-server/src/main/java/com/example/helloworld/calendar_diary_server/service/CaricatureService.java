package com.example.helloworld.calendar_diary_server.service;

import com.example.helloworld.calendar_diary_server.dto.CaricatureDto;
import com.example.helloworld.calendar_diary_server.entity.Caricature;
import com.example.helloworld.calendar_diary_server.entity.DiaryPhoto;
import com.example.helloworld.calendar_diary_server.repository.CaricatureRepository;
import com.example.helloworld.calendar_diary_server.repository.DiaryPhotoRepository;
import com.example.helloworld.calendar_diary_server.service.gen.GmsDalleEditClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CaricatureService {

    private final DiaryPhotoRepository diaryPhotoRepository;
    private final CaricatureRepository caricatureRepository;
    private final S3StorageService s3;
    private final GmsDalleEditClient dalleEditClient;  // ← 새로운 Edit 클라이언트
    private final DiaryService diaryService;

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Transactional
    public CaricatureDto generateFromPhoto(Long coupleId, Long diaryPhotoId) throws Exception {
        DiaryPhoto photo = diaryPhotoRepository.findById(diaryPhotoId)
                .orElseThrow(() -> new IllegalArgumentException("사진이 존재하지 않습니다."));
        if (!photo.getDiary().getCoupleId().equals(coupleId)) {
            throw new IllegalArgumentException("해당 커플의 사진이 아닙니다.");
        }
        if (!photo.isUltrasound()) {
            throw new IllegalArgumentException("초음파 사진이 아닙니다.");
        }

        try {
            // 1) S3에서 원본 초음파 이미지를 바이트 배열로 다운로드
            byte[] originalImageBytes = downloadImageFromS3(photo.getImageKey());

            // 2) 캐리커처 변환 프롬프트 (URL 없이 순수 텍스트만)
            String prompt = """
                    Transform this ultrasound image into a cute, heartwarming baby caricature.
                    Style: clean line art, high contrast, soft shading, gentle smile.
                    Background: simple, warm tone. Make it look like an adorable cartoon baby character.
                    Keep the overall shape and positioning from the original image but convert it to a sweet baby illustration.
                    """;

            // 3) DALL-E Edit API로 초음파 이미지를 캐리커처로 변환
            byte[] caricatureBytes = dalleEditClient.editImage(originalImageBytes, prompt);

            // 4) S3 caricatures/ 업로드
            var up = s3.uploadBytes("caricatures", "caricature.png", "image/png", caricatureBytes);

            // 5) DB 저장
            Caricature c = caricatureRepository.save(Caricature.builder()
                    .diaryPhoto(photo)
                    .imageKey(up.key())
                    .createdAt(ZonedDateTime.now(ZONE))
                    .build());

            // 6) presigned URL 반환(10분)
            return CaricatureDto.builder()
                    .id(c.getId())
                    .diaryPhotoId(photo.getDiaryPhotoId())
                    .imageUrl(diaryService.presignImage(up.key()))
                    .build();

        } catch (Exception e) {
            log.error("Failed to generate caricature for diaryPhotoId: {}", diaryPhotoId, e);
            throw new RuntimeException("캐리커처 생성에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * S3에서 이미지를 바이트 배열로 다운로드
     */
    private byte[] downloadImageFromS3(String imageKey) throws IOException {
        try {
            // DiaryService의 presignImage를 사용해서 URL 생성 후 다운로드
            String presignedUrl = diaryService.presignImage(imageKey);

            try (InputStream inputStream = new URL(presignedUrl).openStream()) {
                return inputStream.readAllBytes();
            }
        } catch (IOException e) {
            log.error("Failed to download image from S3, key: {}", imageKey, e);
            throw new IOException("S3에서 이미지 다운로드 실패: " + imageKey, e);
        }
    }

    /** 최근 생성본 presigned URL 조회 */
    public CaricatureDto getLatest(Long diaryPhotoId) {
        return caricatureRepository.findFirstByDiaryPhoto_DiaryPhotoIdOrderByIdDesc(diaryPhotoId)
                .map(c -> CaricatureDto.builder()
                        .id(c.getId())
                        .diaryPhotoId(diaryPhotoId)
                        .imageUrl(diaryService.presignImage(c.getImageKey()))
                        .build())
                .orElse(null);
    }
}