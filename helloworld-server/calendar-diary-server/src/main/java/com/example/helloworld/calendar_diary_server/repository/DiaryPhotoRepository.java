package com.example.helloworld.calendar_diary_server.repository;

import com.example.helloworld.calendar_diary_server.entity.DiaryPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DiaryPhotoRepository  extends JpaRepository<DiaryPhoto, Long> {
    // 다이어리 대표 이미지(첫 장)
    Optional<DiaryPhoto> findFirstByDiary_DiaryIdOrderByDiaryPhotoIdAsc(Long diaryId);

    // 다이어리 모든 이미지
    List<DiaryPhoto> findAllByDiary_DiaryIdOrderByDiaryPhotoIdAsc(Long diaryId);

    // 커플 전체 이미지(주마등용)
    List<DiaryPhoto> findAllByDiary_CoupleIdOrderByDiary_DiaryIdDescDiaryPhotoIdDesc(Long coupleId);

    // 수정 시 간단 교체용
    void deleteByDiary_DiaryId(Long diaryId);
}
