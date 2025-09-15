package com.example.helloworld.calendar_diary_server.repository;

import com.example.helloworld.calendar_diary_server.entity.Diary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;


public interface DiaryRepository extends JpaRepository<Diary, Long> {
    Page<Diary> findAllByCoupleIdOrderByCreatedAtDesc(Long coupleId, Pageable pageable);

    boolean existsByCoupleIdAndCreatedAtBetween(Long coupleId,
                                                ZonedDateTime startInclusive, ZonedDateTime endExclusive);
}
