package com.example.helloworld.healthserver.persistence;


import com.example.helloworld.healthserver.entity.StepsData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface StepsDataRepository extends JpaRepository<StepsData, Long> {

    // 단건 조회(권한검증용)
    StepsData findByStepsIdAndCoupleId(Long stepsId, Long coupleId);

    // 기간 조회(옵션)
    List<StepsData> findByCoupleIdAndDateBetweenOrderByDateDesc(Long coupleId, Instant from, Instant to);

    // 누적/구간 집계(옵션: 나중에 그래프용으로 사용)
    // label: '00-12' | '00-16' | '00-24' 등, avg: 해당 구간 평균 걸음수
    @Query(value = """
    SELECT 0 AS bucket, '00-12' AS label, AVG(sd.steps)::float8 AS avg_steps
    FROM steps_data sd
    WHERE sd.couple_id = :coupleId
      AND sd.steps IS NOT NULL
      AND sd.steps > 0
      AND EXTRACT(HOUR FROM (sd."date" AT TIME ZONE 'Asia/Seoul')) < 12
    UNION ALL
    SELECT 1 AS bucket, '00-16' AS label, AVG(sd.steps)::float8 AS avg_steps
    FROM steps_data sd
    WHERE sd.couple_id = :coupleId
      AND sd.steps IS NOT NULL
      AND sd.steps > 0
      AND EXTRACT(HOUR FROM (sd."date" AT TIME ZONE 'Asia/Seoul')) < 16
    UNION ALL
    SELECT 2 AS bucket, '00-24' AS label, AVG(sd.steps)::float8 AS avg_steps
    FROM steps_data sd
    WHERE sd.couple_id = :coupleId
      AND sd.steps IS NOT NULL
      AND sd.steps > 0
      AND EXTRACT(HOUR FROM (sd."date" AT TIME ZONE 'Asia/Seoul')) < 24
    ORDER BY bucket
    """, nativeQuery = true)
    List<Object[]> aggregateStepsOverallCumulative(@Param("coupleId") Long coupleId);
}