package com.example.helloworld.calendar_diary_server.service;

import com.example.helloworld.calendar_diary_server.dto.CaricatureDto;
import com.example.helloworld.calendar_diary_server.entity.Caricature;
import com.example.helloworld.calendar_diary_server.entity.DiaryPhoto;
import com.example.helloworld.calendar_diary_server.repository.CaricatureRepository;
import com.example.helloworld.calendar_diary_server.repository.DiaryPhotoRepository;
import com.example.helloworld.calendar_diary_server.service.gen.GmsImageGenClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CaricatureService {

    private final DiaryPhotoRepository diaryPhotoRepository;
    private final CaricatureRepository caricatureRepository;
    private final S3StorageService s3;
    private final GmsImageGenClient gms;

    // ★ 추가: presigned URL 만들 때 DiaryService의 메서드 사용
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

        // 1) 원본 다운로드
        byte[] source = s3.download(photo.getImageKey());

        // 2) GMS 호출
        byte[] out = gms.generateCaricature(source);

        // 3) 결과 업로드 (caricatures/)
        var up = s3.uploadBytes("caricatures", "caricature.png", "image/png", out);

        // 4) DB 저장
        Caricature c = caricatureRepository.save(Caricature.builder()
                .diaryPhoto(photo)
                .imageKey(up.key())
                .createdAt(ZonedDateTime.now(ZONE))
                .build());

        // 5) presigned URL은 DiaryService의 메서드로 생성
        String url = diaryService.presignImage(up.key());

        return CaricatureDto.builder()
                .id(c.getId())
                .diaryPhotoId(photo.getDiaryPhotoId())
                .imageUrl(url)
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
