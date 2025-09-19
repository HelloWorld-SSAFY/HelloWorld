package com.example.helloworld.weeklyserver.repository;

import com.example.helloworld.weeklyserver.entity.WeeklyInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WeeklyInfoRepo extends JpaRepository<WeeklyInfo, Integer> {}