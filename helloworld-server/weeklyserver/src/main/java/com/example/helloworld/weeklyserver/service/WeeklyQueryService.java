package com.example.helloworld.weeklyserver.service;

import com.example.helloworld.weeklyserver.dto.*;
import com.example.helloworld.weeklyserver.entity.WeeklyWorkout;
import com.example.helloworld.weeklyserver.repository.DietPlanRepo;
import com.example.helloworld.weeklyserver.repository.WeeklyInfoRepo;
import com.example.helloworld.weeklyserver.repository.WeeklyWorkoutRepo;
import com.example.helloworld.weeklyserver.infra.YoutubeSearchClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class WeeklyQueryService {
    private final WeeklyInfoRepo infoRepo;
    private final WeeklyWorkoutRepo workoutRepo;
    private final DietPlanRepo dietRepo;
    private final S3UrlService s3url;
    private final YoutubeSearchClient youtube;

    public WeeklyInfoRes getInfo(int weekNo) {
        var info = infoRepo.findById((weekNo))
                .orElseThrow(() -> new NoSuchElementException("week not found"));
        return new WeeklyInfoRes(weekNo, info.getInfoText());
    }

    @Transactional
    public WeeklyWorkoutsRes getWorkouts(int weekNo, boolean refresh) {
        var list = workoutRepo.findByWeekNoOrderByOrderNoAscWorkoutIdAsc(weekNo)
                .stream()
                .map(w -> toWorkoutItemResWithYoutube(w, refresh))
                .toList();
        return new WeeklyWorkoutsRes(weekNo, list);
    }

    private static final Duration YT_TTL = Duration.ofDays(1);

    private boolean isExpired(WeeklyWorkout w) {
        Instant ts = w.getVideoSyncedAt();
        return ts == null || ts.isBefore(Instant.now().minus(YT_TTL));
    }

    private WorkoutItemRes toWorkoutItemResWithYoutube(WeeklyWorkout w, boolean refresh) {
        if (w.getType() == WorkoutType.VIDEO) {
            boolean missing = isBlank(w.getVideoUrl()) || isBlank(w.getThumbnailUrl()) || isBlank(w.getVideoTitle());
            boolean needLookup = refresh || missing || isExpired(w);

            if (needLookup) {
                String base = w.getTextBody();
                String query = "임산부를 위한 " + base;

                youtube.searchFirst(query).ifPresent(result -> {
                    // 검색 성공시에만 값/타임스탬프 갱신
                    w.setVideoTitle(isBlank(w.getVideoTitle()) ? result.getTitle() : w.getVideoTitle());
                    w.setVideoUrl(result.getUrl());
                    w.setThumbnailUrl(result.getThumbnailUrl());
                    w.setVideoSyncedAt(Instant.now());
                    workoutRepo.save(w);
                });
            }

            return new WorkoutItemRes(
                    w.getType(),
                    null,
                    w.getVideoTitle(),
                    w.getVideoUrl(),
                    w.getThumbnailUrl(),
                    w.getOrderNo()
            );
        }

        // TEXT
        return new WorkoutItemRes(
                w.getType(),
                w.getTextBody(),
                null,
                null,
                w.getThumbnailUrl(),
                w.getOrderNo()
        );
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }

    public WeeklyDietsRes getDiets(int weekNo, Integer from, Integer to) {
        int f = (from==null) ? 1 : from;
        int t = (to==null) ? 7 : to;
        var list = dietRepo.findWeekRange(weekNo, f, t).stream()
                .map(d -> new DietDayRes(
                        d.getDayInWeek(),
                        d.getDayNo(),
                        d.getFood(),
                        d.getDetail(),
                        s3url.presignGet(d.getImgUrl(), java.time.Duration.ofMinutes(10))
                ))
                .toList();
        return new WeeklyDietsRes(weekNo, f, t, list);
    }
}
