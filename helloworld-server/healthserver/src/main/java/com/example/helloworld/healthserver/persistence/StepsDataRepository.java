package com.example.helloworld.healthserver.persistence;


import com.example.helloworld.healthserver.entity.StepsData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface StepsDataRepository extends JpaRepository<StepsData, Long> {

    // 단건 조회(권한검증용)
    StepsData findByStepsIdAndCoupleId(Long stepsId, Long coupleId);

    // 기간 조회(옵션)
    List<StepsData> findByCoupleIdAndDateBetweenOrderByDateDesc(Long coupleId, Instant from, Instant to);

    // 누적/구간 집계(옵션: 나중에 그래프용으로 사용)
    // label: '00-12' | '00-16' | '00-24' 등, avg: 해당 구간 평균 걸음수
    @Query("""
           select s.coupleId,
                  case
                      when extract(hour from s.date) < 12 then '00-12'
                      when extract(hour from s.date) < 16 then '00-16'
                      else '00-24'
                  end as label,
                  avg(coalesce(s.steps,0)) as avg_steps
             from StepsData s
            where s.coupleId = :coupleId
            group by s.coupleId, label
            order by label
           """)
    List<Object[]> aggregateStepsOverallCumulative(Long coupleId);
}