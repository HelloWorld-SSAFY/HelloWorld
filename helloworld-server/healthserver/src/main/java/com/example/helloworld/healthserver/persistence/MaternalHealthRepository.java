package com.example.helloworld.healthserver.persistence;

import com.example.helloworld.healthserver.entity.MaternalHealth;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface MaternalHealthRepository extends JpaRepository<MaternalHealth, Long> {

    Page<MaternalHealth> findByCoupleIdOrderByRecordDateDescCreatedAtDesc(Long coupleId, Pageable pageable);

    Page<MaternalHealth> findByCoupleIdAndRecordDateBetweenOrderByRecordDateDescCreatedAtDesc(
            Long coupleId, LocalDate from, LocalDate to, Pageable pageable);

    Optional<MaternalHealth> findTopByCoupleIdOrderByRecordDateDescCreatedAtDesc(Long coupleId);

    Optional<MaternalHealth> findByIdAndCoupleId(Long id, Long coupleId);
}
