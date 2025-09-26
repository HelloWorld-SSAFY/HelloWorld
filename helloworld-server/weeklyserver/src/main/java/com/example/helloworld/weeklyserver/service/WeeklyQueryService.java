package com.example.helloworld.weeklyserver.service;

import com.example.helloworld.weeklyserver.dto.*;
import com.example.helloworld.weeklyserver.repository.DietPlanRepo;
import com.example.helloworld.weeklyserver.repository.WeeklyInfoRepo;
import com.example.helloworld.weeklyserver.repository.WeeklyWorkoutRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class WeeklyQueryService {
    private final WeeklyInfoRepo infoRepo;
    private final WeeklyWorkoutRepo workoutRepo;
    private final DietPlanRepo dietRepo;
    private final S3UrlService s3url;

    public WeeklyInfoRes getInfo(int weekNo) {
        var info = infoRepo.findById((weekNo))
                .orElseThrow(() -> new NoSuchElementException("week not found"));
        return new WeeklyInfoRes(weekNo, info.getInfoText());
    }

    public WeeklyWorkoutsRes getWorkouts(int weekNo) {
        var list = workoutRepo.findByWeekNoOrderByOrderNoAscWorkoutIdAsc(weekNo)
                .stream()
                .map(w -> new WorkoutItemRes(
                        w.getType(),
                        w.getType()== WorkoutType.TEXT ? w.getTextBody() : null,
                        w.getType()==WorkoutType.VIDEO ? w.getVideoTitle() : null,
                        w.getType()==WorkoutType.VIDEO ? w.getVideoUrl() : null,
                        w.getThumbnailUrl(),
                        w.getOrderNo()
                ))
                .toList();
        return new WeeklyWorkoutsRes(weekNo, list);
    }

    public WeeklyDietsRes getDiets(int weekNo, Integer from, Integer to) {
        int f = (from==null) ? 1 : from;
        int t = (to==null) ? 7 : to;
        var list = dietRepo.findWeekRange(weekNo, f, t).stream()
                .map(d -> new DietDayRes(d.getDayInWeek(), d.getDayNo(), d.getFood(), d.getDetail(),  s3url.presignGet(d.getImgUrl(), java.time.Duration.ofMinutes(10))))
                .toList();
        return new WeeklyDietsRes(weekNo, f, t, list);
    }
}
