package com.example.helloworld.calendar_diary_server.repository;

import com.example.helloworld.calendar_diary_server.entity.Diary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;


public interface DiaryRepository extends JpaRepository<Diary, Long> {
    Page<Diary> findAllByCoupleIdOrderByCreatedAtDesc(Long coupleId, Pageable pageable);

    boolean existsByCoupleIdAndCreatedAtBetween(Long coupleId,
                                                ZonedDateTime startInclusive, ZonedDateTime endExclusive);


    // 주차 범위 조회: target_date BETWEEN start..end
    List<Diary> findByCoupleIdAndTargetDateBetweenOrderByTargetDateAscDiaryIdAsc(
            Long coupleId, LocalDate startDate, LocalDate endDate
    );

    // 특정 일차(= 특정 날짜) 조회
    List<Diary> findByCoupleIdAndTargetDateOrderByTargetDateAscDiaryIdAsc(
            Long coupleId, LocalDate targetDate
    );




}
