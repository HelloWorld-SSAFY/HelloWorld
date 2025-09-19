package com.example.helloworld.weeklyserver.repository;

import com.example.helloworld.weeklyserver.entity.WeeklyWorkout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WeeklyWorkoutRepo extends JpaRepository<WeeklyWorkout, Long> {
    List<WeeklyWorkout> findByWeekNoOrderByOrderNoAscWorkoutIdAsc(Integer weekNo);
}
