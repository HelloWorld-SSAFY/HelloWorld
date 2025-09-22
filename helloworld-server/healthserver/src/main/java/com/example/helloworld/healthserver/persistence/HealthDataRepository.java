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



    /**
     * Native Query 결과를 매핑하기 위한 프로젝션 인터페이스
     */
    interface GlobalDailyBucketStats {
        Long getCoupleId();
        // Heart Rate
        Double getAvgHr0(); Double getAvgHr1(); Double getAvgHr2(); Double getAvgHr3(); Double getAvgHr4(); Double getAvgHr5();
        Double getStdHr0(); Double getStdHr1(); Double getStdHr2(); Double getStdHr3(); Double getStdHr4(); Double getStdHr5();
        // Stress
        Double getAvgSt0(); Double getAvgSt1(); Double getAvgSt2(); Double getAvgSt3(); Double getAvgSt4(); Double getAvgSt5();
        Double getStdSt0(); Double getStdSt1(); Double getStdSt2(); Double getStdSt3(); Double getStdSt4(); Double getStdSt5();
    }

    /**
     * 지정된 날짜에 대해 모든 커플의 심박수/스트레스 통계를 4시간 단위 버킷으로 집계합니다.
     */
    @Query(value = """
    SELECT
        hd.couple_id                                                                                    AS "coupleId",
        -- Heart Rate AVG
        AVG(hd.heartrate) FILTER (WHERE ((EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul'))::int) / 4) = 0) AS "avgHr0",
        AVG(hd.heartrate) FILTER (WHERE ((EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul'))::int) / 4) = 1) AS "avgHr1",
        AVG(hd.heartrate) FILTER (WHERE ((EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul'))::int) / 4) = 2) AS "avgHr2",
        AVG(hd.heartrate) FILTER (WHERE ((EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul'))::int) / 4) = 3) AS "avgHr3",
        AVG(hd.heartrate) FILTER (WHERE ((EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul'))::int) / 4) = 4) AS "avgHr4",
        AVG(hd.heartrate) FILTER (WHERE ((EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul'))::int) / 4) = 5) AS "avgHr5",
        -- Heart Rate STDDEV
        STDDEV_SAMP(hd.heartrate) FILTER (WHERE ((EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul'))::int) / 4) = 0) AS "stdHr0",
        STDDEV_SAMP(hd.heartrate) FILTER (WHERE ((EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul'))::int) / 4) = 1) AS "stdHr1",
        STDDEV_SAMP(hd.heartrate) FILTER (WHERE ((EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul'))::int) / 4) = 2) AS "stdHr2",
        STDDEV_SAMP(hd.heartrate) FILTER (WHERE ((EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul'))::int) / 4) = 3) AS "stdHr3",
        STDDEV_SAMP(hd.heartrate) FILTER (WHERE ((EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul'))::int) / 4) = 4) AS "stdHr4",
        STDDEV_SAMP(hd.heartrate) FILTER (WHERE ((EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul'))::int) / 4) = 5) AS "stdHr5",
        -- Stress AVG
        AVG(hd.stress) FILTER (WHERE ((EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul'))::int) / 4) = 0) AS "avgSt0",
        AVG(hd.stress) FILTER (WHERE ((EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul'))::int) / 4) = 1) AS "avgSt1",
        AVG(hd.stress) FILTER (WHERE ((EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul'))::int) / 4) = 2) AS "avgSt2",
        AVG(hd.stress) FILTER (WHERE ((EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul'))::int) / 4) = 3) AS "avgSt3",
        AVG(hd.stress) FILTER (WHERE ((EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul'))::int) / 4) = 4) AS "avgSt4",
        AVG(hd.stress) FILTER (WHERE ((EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul'))::int) / 4) = 5) AS "avgSt5",
        -- Stress STDDEV
        STDDEV_SAMP(hd.stress) FILTER (WHERE ((EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul'))::int) / 4) = 0) AS "stdSt0",
        STDDEV_SAMP(hd.stress) FILTER (WHERE ((EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul'))::int) / 4) = 1) AS "stdSt1",
        STDDEV_SAMP(hd.stress) FILTER (WHERE ((EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul'))::int) / 4) = 2) AS "stdSt2",
        STDDEV_SAMP(hd.stress) FILTER (WHERE ((EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul'))::int) / 4) = 3) AS "stdSt3",
        STDDEV_SAMP(hd.stress) FILTER (WHERE ((EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul'))::int) / 4) = 4) AS "stdSt4",
        STDDEV_SAMP(hd.stress) FILTER (WHERE ((EXTRACT(HOUR FROM (hd."date" AT TIME ZONE 'Asia/Seoul'))::int) / 4) = 5) AS "stdSt5"
    FROM
        health_data hd
    WHERE
        hd."date" >= :from AND hd."date" < :to
    GROUP BY
        hd.couple_id
    """, nativeQuery = true)
    List<GlobalDailyBucketStats> aggregateGlobalDailyBuckets(@Param("from") Instant from, @Param("to") Instant to);





}


