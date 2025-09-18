package com.example.helloworld.healthserver.persistence;

import com.example.helloworld.healthserver.entity.HealthData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HealthDataRepository extends JpaRepository<HealthData, Long> {

    Optional<HealthData> findByHealthIdAndCoupleId(Long healthId, Long coupleId);

    List<HealthData> findByCoupleIdOrderByDateDesc(Long coupleId);

    List<HealthData> findByCoupleIdAndDateBetweenOrderByDateDesc(
            Long coupleId, Instant from, Instant to);

    // 심박수 일별 평균/표준편차 (Asia/Seoul 기준 하루, heartrate null 제외)
    @Query(value = """
    SELECT
        ((EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul'))::int) / 4) AS bucket,
        AVG(hd.heartrate)::float8                       AS avg_hr,
        STDDEV_SAMP(hd.heartrate)::float8               AS stddev_hr,
        COUNT(hd.heartrate)                              AS cnt_hr
    FROM health_data hd
    WHERE hd.couple_id = :coupleId
      AND hd."date" >= :from
      AND hd."date" <  :to
      AND hd.heartrate IS NOT NULL
      AND hd.heartrate > 45
      AND hd.heartrate < 150
    GROUP BY bucket
    ORDER BY bucket
    """, nativeQuery = true)
    List<Object[]> aggregateHeartRateBuckets(@Param("coupleId") Long coupleId,
                                             @Param("from") Instant from,
                                             @Param("to") Instant to);

    @Query(value = """
    SELECT 0 AS bucket, '00-12' AS label, AVG(hd.steps)::float8 AS avg_steps
    FROM health_data hd
    WHERE hd.couple_id = :coupleId
      AND hd.steps IS NOT NULL
      AND hd.steps > 0
      AND EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul')) < 12
    UNION ALL
    SELECT 1 AS bucket, '00-16' AS label, AVG(hd.steps)::float8 AS avg_steps
    FROM health_data hd
    WHERE hd.couple_id = :coupleId
      AND hd.steps IS NOT NULL
      AND hd.steps > 0
      AND EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul')) < 16
    UNION ALL
    SELECT 2 AS bucket, '00-24' AS label, AVG(hd.steps)::float8 AS avg_steps
    FROM health_data hd
    WHERE hd.couple_id = :coupleId
      AND hd.steps IS NOT NULL
      AND hd.steps > 0
      AND EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul')) < 24
    ORDER BY bucket
    """, nativeQuery = true)
    List<Object[]> aggregateStepsOverallCumulative(@Param("coupleId") Long coupleId);
}
