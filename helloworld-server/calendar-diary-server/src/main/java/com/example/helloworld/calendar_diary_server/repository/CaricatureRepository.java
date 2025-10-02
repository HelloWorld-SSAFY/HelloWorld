// repository/CaricatureRepository.java
package com.example.helloworld.calendar_diary_server.repository;

import com.example.helloworld.calendar_diary_server.entity.Caricature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CaricatureRepository extends JpaRepository<Caricature, Long> {
    Optional<Caricature> findFirstByDiaryPhoto_DiaryPhotoIdOrderByIdDesc(Long diaryPhotoId);
    boolean existsByDiaryPhoto_DiaryPhotoId(Long diaryPhotoId);
}
