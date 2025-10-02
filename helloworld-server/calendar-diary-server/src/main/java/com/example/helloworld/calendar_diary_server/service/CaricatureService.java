package com.example.helloworld.calendar_diary_server.service;

import com.example.helloworld.calendar_diary_server.dto.CaricatureDto;
import com.example.helloworld.calendar_diary_server.entity.Caricature;
import com.example.helloworld.calendar_diary_server.entity.DiaryPhoto;
import com.example.helloworld.calendar_diary_server.repository.CaricatureRepository;
import com.example.helloworld.calendar_diary_server.repository.DiaryPhotoRepository;
import com.example.helloworld.calendar_diary_server.service.gen.GmsDalleClient;
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
    private final GmsDalleClient gms;  // ← 원래 클라이언트 사용
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
            // 1) 랜덤 아기 캐리커처 프롬프트 생성
            String prompt = generateRandomBabyCaricaturePrompt();

            log.info("Generating baby caricature for diaryPhotoId: {} with prompt: {}", diaryPhotoId, prompt);

            // 2) DALL-E Generations API로 캐리커처 생성
            byte[] caricatureBytes = gms.generateCaricatureWithPrompt(prompt);

            // 3) S3 caricatures/ 업로드
            var up = s3.uploadBytes("caricatures", "caricature.png", "image/png", caricatureBytes);

            // 4) DB 저장
            Caricature c = caricatureRepository.save(Caricature.builder()
                    .diaryPhoto(photo)
                    .imageKey(up.key())
                    .createdAt(ZonedDateTime.now(ZONE))
                    .build());

            // 5) presigned URL 반환(10분)
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
     * 랜덤한 아기 캐리커처 프롬프트 생성
     */
    private String generateRandomBabyCaricaturePrompt() {
        String[] styles = {
                "cute watercolor style",
                "soft pastel cartoon style",
                "gentle line art style",
                "adorable chibi style",
                "warm sketch style",
                "sweet digital illustration style",
                "cozy storybook style",
                "tender hand-drawn style"
        };

        String[] expressions = {
                "gentle smile",
                "peaceful sleeping expression",
                "curious wide eyes",
                "content happy face",
                "serene calm expression",
                "playful cheerful look",
                "innocent wonder in eyes",
                "sweet dreamy expression"
        };

        String[] features = {
                "chubby cheeks",
                "tiny button nose",
                "soft round face",
                "delicate features",
                "small cute hands",
                "rosy cheeks",
                "fine wispy hair",
                "bright sparkly eyes"
        };

        String[] backgrounds = {
                "soft pastel clouds",
                "gentle rainbow colors",
                "warm golden light",
                "dreamy star patterns",
                "cozy nursery setting",
                "soft cotton candy background",
                "peaceful garden scene",
                "tender moon and stars"
        };

        // 랜덤 선택
        String style = styles[(int) (Math.random() * styles.length)];
        String expression = expressions[(int) (Math.random() * expressions.length)];
        String feature = features[(int) (Math.random() * features.length)];
        String background = backgrounds[(int) (Math.random() * backgrounds.length)];

        return String.format(
                "Create an adorable baby caricature in %s. " +
                        "The baby should have %s with %s and %s. " +
                        "Background: %s. " +
                        "Art style: high quality, heartwarming, family-friendly, clean and polished. " +
                        "Make it look like a precious newborn character that would make parents smile.",
                style, expression, feature, feature, background
        );
    }

    /**
     * S3에서 이미지를 바이트 배열로 다운로드 (사용하지 않지만 남겨둠)
     */
    @SuppressWarnings("unused")
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