package com.example.helloworld.weeklyserver.repository;

import com.example.helloworld.weeklyserver.entity.DietPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DietPlanRepo extends JpaRepository<DietPlan, Long> {
    @Query("""
    SELECT d FROM DietPlan d
    WHERE d.weekNo = :weekNo AND d.dayInWeek BETWEEN :from AND :to
    ORDER BY d.dayInWeek ASC, d.dietId ASC
  """)
    List<DietPlan> findWeekRange(@Param("weekNo") Integer weekNo,
                                 @Param("from") Integer from,
                                 @Param("to") Integer to);
}
