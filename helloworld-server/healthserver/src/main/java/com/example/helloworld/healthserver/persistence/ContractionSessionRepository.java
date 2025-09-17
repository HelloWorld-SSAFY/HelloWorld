package com.example.helloworld.healthserver.persistence;


import com.example.helloworld.healthserver.entity.ContractionSession;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface ContractionSessionRepository extends JpaRepository<ContractionSession, Long> {

    Optional<ContractionSession> findTopByCoupleIdOrderByEndTimeDesc(Long coupleId);

    Page<ContractionSession> findByCoupleIdOrderByStartTimeDesc(Long coupleId, Pageable pageable);

    Page<ContractionSession> findByCoupleIdAndStartTimeBetweenOrderByStartTimeDesc(
            Long coupleId, Instant from, Instant to, Pageable pageable);
}