package com.example.helloworld.calendar_diary_server.service;


import com.example.helloworld.calendar_diary_server.dto.*;

import com.example.helloworld.calendar_diary_server.entity.Diary;
import com.example.helloworld.calendar_diary_server.entity.DiaryPhoto;
import com.example.helloworld.calendar_diary_server.repository.DiaryPhotoRepository;
import com.example.helloworld.calendar_diary_server.repository.DiaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final DiaryRepository diaryRepository;
    private final DiaryPhotoRepository diaryPhotoRepository;

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // 주차 조회: week(1..40), lmpDate, coupleId
    public WeekResult getByWeek(Long coupleId, int week, LocalDate lmpDate) {
        validateWeek(week);
        if (lmpDate == null) throw new IllegalArgumentException("lmpDate is required");

        int startDay = (week - 1) * 7 + 1;
        int endDay   = week * 7;

        LocalDate startDate = lmpDate.plusDays(startDay - 1L);
        LocalDate endDate   = lmpDate.plusDays(endDay - 1L);

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

        LocalDate date = lmpDate.plusDays(day - 1L);
        int week = ((day - 1) / 7) + 1;

        List<DiaryResponse> items = diaryRepository
                .findByCoupleIdAndTargetDateOrderByTargetDateAscDiaryIdAsc(coupleId, date)
                .stream().map(DiaryResponse::from).toList();

        return new DayResult(
                coupleId, day, week, date, items.size(), items
        );
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
                            .map(DiaryPhoto::getImageUrl).orElse(null);
                    return DiaryListItemDto.builder()
                            .diaryId(String.valueOf(d.getDiaryId()))
                            .createdAt(DAY.format(d.getCreatedAt().withZoneSameInstant(ZONE).toLocalDate()))
                            .diaryTitle(d.getDiaryTitle())
                            .imageUrl(cover)
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
                .map(DiaryPhoto::getImageUrl)
                .orElse(null);

        // 전체 이미지
        List<DiaryDetailPhotoDto> images = diaryPhotoRepository
                .findAllByDiary_DiaryIdOrderByDiaryPhotoIdAsc(diaryId)
                .stream()
                .map(p -> DiaryDetailPhotoDto.builder()
                        .imageUrl(p.getImageUrl())
                        .isUltrasound(p.isUltrasound())
                        .build())
                .toList();

        return DiaryDetailDto.builder()
                .createdAt(DAY.format(d.getCreatedAt().withZoneSameInstant(ZONE).toLocalDate()))
                .diaryTitle(d.getDiaryTitle())
                .diaryContent(d.getDiaryContent())
                .imageUrl(cover)      // 호환성 위해 유지
                .images(images)       // 신규: 전체 이미지
                .authorId(String.valueOf(d.getAuthorId()))
                .authorRole(d.getAuthorRole().name().toLowerCase())
                .build();
    }

    /** 6.3 일기 작성 */
    @Transactional
    public Long create(CreateDiaryRequest req) {
        LocalDate entry = LocalDate.parse(req.getEntryDate(), DAY);
        ZonedDateTime startOfDay = entry.atStartOfDay(ZONE);

        boolean dup = diaryRepository.existsByCoupleIdAndCreatedAtBetween(
                req.getCoupleId(), startOfDay, startOfDay.plusDays(1));
        if (dup) throw new IllegalStateException("해당 날짜 일기가 이미 존재합니다.");

        Diary d = Diary.builder()
                .coupleId(req.getCoupleId())
                .authorId(req.getAuthorId())
                .authorRole("female".equalsIgnoreCase(req.getAuthorRole()) ?
                        Diary.AuthorRole.MALE : Diary.AuthorRole.FEMALE)
                .diaryTitle(req.getDiaryTitle())
                .diaryContent(req.getDiaryContent())
                .createdAt(startOfDay)
                .targetDate(req.getTargetDate())
                .build();

        d = diaryRepository.save(d);

        if (req.getImageUrl() != null && !req.getImageUrl().isBlank()) {
            DiaryPhoto p = DiaryPhoto.builder()
                    .diary(d)
                    .imageUrl(req.getImageUrl())
                    .isUltrasound(false)
                    .build();
            diaryPhotoRepository.save(p);
        }
        return d.getDiaryId();
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
                        .diary(d).imageUrl(req.getImageUrl()).isUltrasound(false).build());
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
                        .imageUrl(p.getImageUrl())
                        .isUltrasound(p.isUltrasound())
                        .build())
                .toList();
    }
}
