package com.example.helloworld.weeklyserver.service;

import com.example.helloworld.weeklyserver.dto.*;
import com.example.helloworld.weeklyserver.entity.WeeklyWorkout;
import com.example.helloworld.weeklyserver.repository.DietPlanRepo;
import com.example.helloworld.weeklyserver.repository.WeeklyInfoRepo;
import com.example.helloworld.weeklyserver.repository.WeeklyWorkoutRepo;
import com.example.helloworld.weeklyserver.infra.YoutubeSearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;

@Slf4j
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
        log.info("[Service] Processing workout - id: {}, type: {}, existing title: {}, existing url: {}",
                w.getWorkoutId(), w.getType(), w.getVideoTitle(), w.getVideoUrl());

        if (w.getType() == WorkoutType.VIDEO) {
            boolean missing = isBlank(w.getVideoUrl()) || isBlank(w.getThumbnailUrl()) || isBlank(w.getVideoTitle());
            boolean needLookup = refresh || missing || isExpired(w);

            log.info("[Service] Video workout - missing: {}, needLookup: {}, refresh: {}", missing, needLookup, refresh);

            if (needLookup) {
                String base = !isBlank(w.getVideoTitle()) ? w.getVideoTitle()
                        : !isBlank(w.getTextBody())  ? w.getTextBody()
                        : "스트레칭";
                base = base.replaceAll("\\s*영상\\d*$","").replaceAll("\\s+"," ").trim();

                String query = "임산부를 위한 " + base;   // ← 맨 위 결과 그대로 사용

                log.info("[Service] Searching YouTube with query: '{}'", query);

                youtube.searchFirst(query).ifPresentOrElse(result -> {
                    // 검색 성공시에만 값/타임스탬프 갱신
                    log.info("[Service] YouTube search SUCCESS - videoId: {}, title: {}, url: {}, thumbnail: {}",
                            result.getVideoId(), result.getTitle(), result.getUrl(), result.getThumbnailUrl());

                    String originalTitle = w.getVideoTitle();
                    String newTitle = isBlank(w.getVideoTitle()) ? result.getTitle() : w.getVideoTitle();

                    w.setVideoTitle(newTitle);
                    w.setVideoUrl(result.getUrl());
                    w.setThumbnailUrl(result.getThumbnailUrl());
                    w.setVideoSyncedAt(Instant.now());

                    log.info("[Service] About to SAVE - original title: '{}', new title: '{}', url: '{}', thumbnail: '{}'",
                            originalTitle, newTitle, result.getUrl(), result.getThumbnailUrl());

                    WeeklyWorkout saved = workoutRepo.save(w);

                    log.info("[Service] SAVED workout - id: {}, saved title: '{}', saved url: '{}', saved thumbnail: '{}'",
                            saved.getWorkoutId(), saved.getVideoTitle(), saved.getVideoUrl(), saved.getThumbnailUrl());

                }, () -> {
                    log.warn("[Service] YouTube search FAILED for query: '{}'", query);
                });
            }

            log.info("[Service] Returning video result - title: '{}', url: '{}', thumbnail: '{}'",
                    w.getVideoTitle(), w.getVideoUrl(), w.getThumbnailUrl());

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
        log.info("[Service] Returning text result - textBody: '{}'", w.getTextBody());
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
