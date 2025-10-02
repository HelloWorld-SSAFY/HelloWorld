package com.example.helloworld.healthserver.persistence;


import com.example.helloworld.healthserver.entity.ContractionSession;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.time.Instant;
import java.util.Optional;

public interface ContractionSessionRepository extends JpaRepository<ContractionSession, Long> {

    Optional<ContractionSession> findTopByCoupleIdOrderByEndTimeDesc(Long coupleId);

    List<ContractionSession> findByCoupleIdAndStartTimeBetweenOrderByStartTimeDesc(
            Long coupleId, Instant start, Instant end);

    List<ContractionSession> findByCoupleIdOrderByStartTimeDesc(Long coupleId);


   }