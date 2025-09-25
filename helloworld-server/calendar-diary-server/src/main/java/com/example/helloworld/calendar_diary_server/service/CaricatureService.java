package com.example.helloworld.calendar_diary_server.service;

import com.example.helloworld.calendar_diary_server.dto.CaricatureDto;
import com.example.helloworld.calendar_diary_server.entity.Caricature;
import com.example.helloworld.calendar_diary_server.entity.DiaryPhoto;
import com.example.helloworld.calendar_diary_server.repository.CaricatureRepository;
import com.example.helloworld.calendar_diary_server.repository.DiaryPhotoRepository;
import com.example.helloworld.calendar_diary_server.service.gen.GmsDalleClient;
import com.example.helloworld.calendar_diary_server.service.gen.GmsImageGenClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;

// service/CaricatureService.java (핵심 부분만)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CaricatureService {

    private final DiaryPhotoRepository diaryPhotoRepository;
    private final CaricatureRepository caricatureRepository;
    private final S3StorageService s3;
    private final GmsDalleClient gms;        // ← 구현체 주입
    private final DiaryService diaryService; // ← presign 재사용을 위해 주입

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

        // 1) 원본 presigned URL (inline/content-type 강제는 DiaryService 쪽 구현 사용)
        String srcUrl = diaryService.presignImage(photo.getImageKey());

        // 2) 프롬프트 구성 (게이트웨이/운영팀 가이드에 맞춰 문구 조절)
        String prompt = """
                Create a cute, heartwarming caricature of a baby derived from this ultrasound image:
                %s
                Style: clean line art, high contrast, soft shading, gentle smile.
                Background: simple, warm tone. Output as a single centered character.
                """.formatted(srcUrl);

        // 3) GMS 호출 (텍스트→이미지)
        byte[] out = gms.generateCaricatureWithPrompt(prompt);

        // 4) S3 caricatures/ 업로드
        var up = s3.uploadBytes("caricatures", "caricature.png", "image/png", out);

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
    }


    /** 최근 생성본 presigned URL 조회 */
    public CaricatureDto getLatest(Long diaryPhotoId) {
        return caricatureRepository.findFirstByDiaryPhoto_DiaryPhotoIdOrderByIdDesc(diaryPhotoId)
                .map(c -> CaricatureDto.builder()
                        .id(c.getId())
                        .diaryPhotoId(diaryPhotoId)
                        .imageUrl(diaryService.presignImage(c.getImageKey())) // ★ 여기서 사용
                        .build())
                .orElse(null);
    }
}



