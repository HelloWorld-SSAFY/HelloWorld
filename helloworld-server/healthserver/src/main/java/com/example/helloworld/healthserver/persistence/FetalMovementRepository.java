package com.example.helloworld.healthserver.persistence;


import com.example.helloworld.healthserver.entity.FetalMovement;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface FetalMovementRepository extends JpaRepository<FetalMovement, Long> {

    List<FetalMovement> findByCoupleIdAndRecordedAtBetweenOrderByRecordedAtDesc(
            Long coupleId, Instant from, Instant to);

    Page<FetalMovement> findByCoupleIdOrderByRecordedAtDesc(Long coupleId, Pageable pageable);

    @Query(value = """
        SELECT ((recorded_at AT TIME ZONE 'UTC')::date) AS day, COUNT(*) AS total
        FROM fetal_movements
        WHERE couple_id = :coupleId
          AND recorded_at >= :from
          AND recorded_at <  :to
        GROUP BY day
        ORDER BY day ASC
        """, nativeQuery = true)
    List<Object[]> aggregateByDay(@Param("coupleId") Long coupleId,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to);

}