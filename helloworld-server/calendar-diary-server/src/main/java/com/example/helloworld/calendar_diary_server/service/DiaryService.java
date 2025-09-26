package com.example.helloworld.calendar_diary_server.service;


import com.example.helloworld.calendar_diary_server.dto.*;

import com.example.helloworld.calendar_diary_server.entity.Diary;
import com.example.helloworld.calendar_diary_server.entity.DiaryPhoto;
import com.example.helloworld.calendar_diary_server.repository.DiaryPhotoRepository;
import com.example.helloworld.calendar_diary_server.repository.DiaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiaryService {

    // DiaryService 안쪽(클래스 내부)에 정의
    public static record ImageItem(String key, boolean isUltrasound) {}

    private final DiaryRepository diaryRepository;
    private final DiaryPhotoRepository diaryPhotoRepository;
    private final S3Presigner presigner;
    private final com.example.helloworld.calendar_diary_server.config.S3Config s3cfg;

    @Value("${app.zone:Asia/Seoul}")
    private String appZone;

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // 주차 조회: week(1..40), lmpDate, coupleId
    public WeekResult getByWeek(Long coupleId, int week, LocalDate lmpDate) {
        validateWeek(week);
        if (lmpDate == null) throw new IllegalArgumentException("lmpDate is required");

        int startDay = (week - 1) * 7 + 1;
        int endDay   = week * 7;

        LocalDate startDate = lmpDate.plusDays(startDay);
        LocalDate endDate   = lmpDate.plusDays(endDay);

        List<DiaryResponse> items = diaryRepository
                .findByCoupleIdAndTargetDateBetweenOrderByTargetDateAscDiaryIdAsc(coupleId, startDate, endDate)
                .stream().map(DiaryResponse::from).toList();

        return new WeekResult(
                coupleId, week, startDate, endDate, items.size(), items
        );
    }

    // 일차 조회: day(1..280), lmpDate, coupleId
    public DayResult getByDay(Long coupleId, int day, LocalDate lmpDate) {
        validateDay(day);
        if (lmpDate == null) throw new IllegalArgumentException("lmpDate is required");

        LocalDate date = lmpDate.plusDays(day);
        int week = ((day - 1) / 7) + 1;

        List<DiaryResponse> items = diaryRepository
                .findByCoupleIdAndTargetDateOrderByTargetDateAscDiaryIdAsc(coupleId, date)
                .stream()
                .map(d -> {
                    // 대표 이미지 key (첫 장)
                    String coverKey = diaryPhotoRepository
                            .findFirstByDiary_DiaryIdOrderByDiaryPhotoIdAsc(d.getDiaryId())
                            .map(p -> p.getImageKey())
                            .orElse(null);
                    String coverUrl = presignGet(coverKey); // key→10분짜리 URL (null 안전)
                    return DiaryResponse.from(d, coverUrl);
                })
                .toList();

        return new DayResult(coupleId, day, week, date, items.size(), items);
    }


    private void validateWeek(int week) {
        if (week < 1 || week > 40) throw new IllegalArgumentException("week must be 1..40");
    }
    private void validateDay(int day) {
        if (day < 1 || day > 280) throw new IllegalArgumentException("day must be 1..280");
    }



    /** 6.1 일기 전체 조회 */
    public Page<DiaryListItemDto> list(Long coupleId, Pageable pageable) {
        return diaryRepository.findAllByCoupleIdOrderByCreatedAtDesc(coupleId, pageable)
                .map(d -> {
                    String cover = diaryPhotoRepository
                            .findFirstByDiary_DiaryIdOrderByDiaryPhotoIdAsc(d.getDiaryId())
                            .map(DiaryPhoto::getImageKey).orElse(null);
                    String coverUrl = presignGet(cover);
                    return DiaryListItemDto.builder()
                            .diaryId(String.valueOf(d.getDiaryId()))
                            .createdAt(DAY.format(d.getCreatedAt().withZoneSameInstant(ZONE).toLocalDate()))
                            .diaryTitle(d.getDiaryTitle())
                            .imageUrl(coverUrl)
                            .authorId(String.valueOf(d.getAuthorId()))
                            .authorRole(d.getAuthorRole().name().toLowerCase())
                            .build();
                });
    }

    /** 6.2 일기 상세 조회 */
    public DiaryDetailDto detail(Long diaryId) {
        Diary d = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new NoSuchElementException("Diary not found"));

        // 대표 이미지(첫 장)
        String cover = diaryPhotoRepository
                .findFirstByDiary_DiaryIdOrderByDiaryPhotoIdAsc(diaryId)
                .map(DiaryPhoto::getImageKey)
                .orElse(null);
        String coverUrl = presignGet(cover);

        // 전체 이미지
        List<DiaryDetailPhotoDto> images = diaryPhotoRepository
                .findAllByDiary_DiaryIdOrderByDiaryPhotoIdAsc(diaryId)
                .stream()
                .map(p -> DiaryDetailPhotoDto.builder()
                        .imageUrl(presignGet(p.getImageKey()))
                        .isUltrasound(p.isUltrasound())
                        .build())
                .toList();

        return DiaryDetailDto.builder()
                .createdAt(DAY.format(d.getCreatedAt().withZoneSameInstant(ZONE).toLocalDate()))
                .diaryTitle(d.getDiaryTitle())
                .diaryContent(d.getDiaryContent())
                .imageUrl(coverUrl)      // 호환성 위해 유지
                .images(images)       // 신규: 전체 이미지
                .authorId(String.valueOf(d.getAuthorId()))
                .authorRole(d.getAuthorRole().name().toLowerCase())
                .build();
    }

    /** 6.3 일기 작성 */
    @Transactional
    public Long create(CreateDiaryRequest req,Long coupleId, Long authorId, String authorRole) {
        LocalDate entry = LocalDate.parse(req.getEntryDate(), DAY);
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(appZone));


        Diary diary = Diary.builder()
                .coupleId(coupleId) // 신뢰할 수 있는 인증 정보 사용
                .authorId(authorId) // 신뢰할 수 있는 인증 정보 사용
                .authorRole(Diary.AuthorRole.valueOf(authorRole.toUpperCase())) // 신뢰할 수 있는 인증 정보 사용
                .diaryTitle(req.getDiaryTitle())
                .diaryContent(req.getDiaryContent())
                .createdAt(now)
                .targetDate(req.getTargetDate())
                .build();

        diaryRepository.save(diary);
        return diary.getDiaryId();
    }

    /** 6.4 일기 수정 (이미지 포함) */
    @Transactional
    public void update(Long diaryId, UpdateDiaryRequest req) {
        Diary d = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new NoSuchElementException("Diary not found"));

        LocalDate entry = LocalDate.parse(req.getEntryDate(), DAY);
        d.setCreatedAt(entry.atStartOfDay(ZONE)); // 문서 요구대로 작성일 변경 허용
        d.setDiaryTitle(req.getDiaryTitle());
        d.setDiaryContent(req.getDiaryContent());

        if (req.getImageUrl() != null) { // null이면 이미지 미변경
            diaryPhotoRepository.deleteByDiary_DiaryId(diaryId);
            if (!req.getImageUrl().isBlank()) {
                diaryPhotoRepository.save(DiaryPhoto.builder()
                        .diary(d).imageKey(req.getImageUrl()).isUltrasound(false).build());
            }
        }
    }

    /** 6.5 일기 삭제 */
    @Transactional
    public void delete(Long diaryId) {
        if (!diaryRepository.existsById(diaryId)) {
            throw new NoSuchElementException("Diary not found");
        }
        diaryRepository.deleteById(diaryId); // FK ON DELETE CASCADE로 사진도 삭제
    }

    /** 6.6 커플의 일기 사진 전체 조회(주마등용) */
    public List<DiaryPhotoDto> allPhotos(Long coupleId) {
        return diaryPhotoRepository
                .findAllByDiary_CoupleIdOrderByDiary_DiaryIdDescDiaryPhotoIdDesc(coupleId)
                .stream()
                .map(p -> DiaryPhotoDto.builder()
                        .diaryId(p.getDiary().getDiaryId())
                        .imageUrl(presignGet(p.getImageKey()))
                        .isUltrasound(p.isUltrasound())
                        .build())
                .toList();
    }



    // --- 단일 대표 이미지 교체(호환용) ---
    @Transactional
    public void updateImage(Long diaryId, Long coupleId, String imageKey) {
        if (imageKey == null || imageKey.isBlank()) {
            replaceImages(diaryId, coupleId, List.of()); // 전체 삭제
            return;
        }
        replaceImages(diaryId, coupleId, List.of(new ImageItem(imageKey.trim(), false)));
    }

    // --- 여러 장 덮어쓰기(통째로 교체) : 권장 시그니처 ---
    @Transactional
    public void replaceImages(Long diaryId, Long coupleId, List<ImageItem> images) {
        var diary = diaryRepository.findByDiaryIdAndCoupleId(diaryId, coupleId)
                .orElseThrow(() -> new IllegalArgumentException("일기 없음 또는 권한 없음"));

        // 1) 기존 전부 삭제
        diaryPhotoRepository.deleteByDiary_DiaryId(diaryId);

        // 2) 새로 삽입
        if (images == null || images.isEmpty()) return;

        images.stream()
                .filter(it -> it != null && it.key() != null && !it.key().isBlank())
                .map(it -> new ImageItem(it.key().trim(), it.isUltrasound()))
                .distinct() // key 중복 방지
                .forEach(it -> diaryPhotoRepository.save(
                        DiaryPhoto.builder()
                                .diary(diary)
                                .imageKey(it.key())
                                .isUltrasound(it.isUltrasound())
                                .build()
                ));
    }



    // DiaryService.java
    private String presignGet(String key) {
        if (key == null || key.isBlank()) return null;
        String mime = mimeFromKey(key); // 확장자로 추정

        var get = GetObjectRequest.builder()
                .bucket(s3cfg.getBucket())
                .key(key)
                .responseContentType(mime)           // ← 응답 헤더 강제
                .responseContentDisposition("inline")// ← 다운로드 말고 화면 표시
                .build();

        return presigner.presignGetObject(b -> b
                .signatureDuration(Duration.ofMinutes(10))
                .getObjectRequest(get)
        ).url().toString();
    }

    private static String mimeFromKey(String key) {
        String ext = key.contains(".") ? key.substring(key.lastIndexOf('.') + 1) : "";
        return switch (ext.toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            case "svg" -> "image/svg+xml";
            case "heic", "heif" -> "image/heic"; // 일부 웹뷰 미표시 가능
            default -> "application/octet-stream";
        };
    }

    public String presignImage(String key) {
        return presignGet(key);   // 기존 로직 재사용
    }

}
