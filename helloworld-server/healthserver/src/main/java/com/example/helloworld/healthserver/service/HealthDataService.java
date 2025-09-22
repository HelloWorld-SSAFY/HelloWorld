package com.example.helloworld.healthserver.service;

import com.example.helloworld.healthserver.alarm.dto.AiResponse;
import com.example.helloworld.healthserver.alarm.dto.AiTelemetryRequest;
import com.example.helloworld.healthserver.alarm.service.AiClient;
import com.example.helloworld.healthserver.dto.HealthDtos;
import com.example.helloworld.healthserver.dto.HealthDtos.*;
import com.example.helloworld.healthserver.entity.HealthData;
import com.example.helloworld.healthserver.persistence.HealthDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class HealthDataService {

    private final HealthDataRepository repo;

    private final AiClient aiClient;

    @Value("${app.zone:Asia/Seoul}")
    private String appZone;

    /** 프론트 /api/wearable 진입점: DB 저장 후 AI 호출, AI 응답을 그대로 리턴 */
    @Transactional
    public AiResponse createAndEvaluate(Long coupleId, HealthDtos.CreateRequest req) {
        // 1) DB 저장 (date nullable이면 now() 대체)
        Instant dt = Optional.ofNullable(req.date()).orElse(Instant.now());
        HealthData saved = repo.save(
                HealthData.builder()
                        .coupleId(coupleId)
                        .date(dt)
                        .stress(req.stress())
                        .heartrate(req.heartrate())
                        .build()
        );

        // 2) AI 요청 바디 구성 (stress: Integer → 0~1.0 스케일)
        Map<String,Object> metrics = new HashMap<>();
        if (req.heartrate() != null) metrics.put("hr", req.heartrate());
        if (req.stress() != null) {
            double s = req.stress();
            metrics.put("stress", (s > 1.0) ? s / 100.0 : s);
        }
        String ts = OffsetDateTime.ofInstant(dt, ZoneId.of(appZone)).toString();

        AiTelemetryRequest aiReq = new AiTelemetryRequest("c" + coupleId, ts, metrics);

        // 3) 백엔드 내부에서 AI 서버 호출 (POST /api/health/telemetry)
        AiResponse ai = aiClient.postTelemetry(aiReq);


        // 5) 프론트에는 AI 응답 그대로 반환
        return ai;
    }


    @Transactional
    public GetResponse create(Long coupleId, CreateRequest req) {
        HealthData hd = HealthData.builder()
                .coupleId(coupleId)
                .date(req.date())
                .stress(req.stress())
                .heartrate(req.heartrate())
//                .steps(req.steps())
                .build();
        hd = repo.save(hd);
        return toGet(hd);
    }

    @Transactional(readOnly = true)
    public GetResponse getById(Long coupleId, Long healthId) {
        var hd = repo.findByHealthIdAndCoupleId(healthId, coupleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found"));
        return toGet(hd);
    }

//    @Transactional(readOnly = true)
//    public ListResponse list(Long coupleId, LocalDate from, LocalDate to) {
//        List<HealthData> rows;
//        if (from == null && to == null) {
//            rows = repo.findByCoupleIdOrderByDateDesc(coupleId);
//        } else {
//            ZoneId zone = ZoneId.of(appZone);
//            LocalDate start = (from != null) ? from : LocalDate.of(1970, 1, 1);
//            LocalDate end   = (to   != null) ? to   : LocalDate.of(9999, 12, 31);
//            Instant fromI = start.atStartOfDay(zone).toInstant();
//            Instant toI   = end.plusDays(1).atStartOfDay(zone).toInstant();
//            rows = repo.findByCoupleIdAndDateBetweenOrderByDateDesc(coupleId, fromI, toI);
//        }
//
//        var items = rows.stream().map(this::toItem).toList();
//        return new ListResponse(items);
//    }

//    @Transactional(readOnly = true)
//    public HrDailyStatsResponse hrDailyStats(Long coupleId, LocalDate from, LocalDate to) {
//        ZoneId zone = ZoneId.of(appZone);
//        LocalDate end = (to != null) ? to : LocalDate.now(zone);
//        LocalDate start = (from != null) ? from : end.minusDays(13);
//
//        Instant fromI = start.atStartOfDay(zone).toInstant();
//        Instant toI   = end.plusDays(1).atStartOfDay(zone).toInstant();
//
//        List<Object[]> rows = repo.aggregateHrDailyStats(coupleId, fromI, toI);
//        List<HrDailyStatsResponse.Item> items = new ArrayList<>(rows.size());
//        for (Object[] r : rows) {
//            java.sql.Date day = (java.sql.Date) r[0];
//            Double avg = (r[1] != null) ? ((Number) r[1]).doubleValue() : null;
//            Double std = (r[2] != null) ? ((Number) r[2]).doubleValue() : null;
//            Long cnt   = (r[3] != null) ? ((Number) r[3]).longValue()   : 0L;
//            items.add(new HrDailyStatsResponse.Item(
//                    day.toLocalDate().toString(), avg, std, cnt
//            ));
//        }
//        return new HrDailyStatsResponse(items);
//    }

    @Transactional(readOnly = true)
    public BucketResponse hrDailyBuckets(Long coupleId, LocalDate date) {
        if (date == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date is required (YYYY-MM-DD)");

        ZoneId zone = ZoneId.of(appZone);
        Instant fromI = date.atStartOfDay(zone).toInstant();
        Instant toI   = date.plusDays(1).atStartOfDay(zone).toInstant();

        Map<Integer, Stat> map = new HashMap<>();
        for (Object[] r : repo.aggregateHeartRateBuckets(coupleId, fromI, toI)) {
            int bucket = ((Number) r[0]).intValue();                  // 0..5
            Double avg = r[1] != null ? ((Number) r[1]).doubleValue() : null;
            Double std = r[2] != null ? ((Number) r[2]).doubleValue() : null;
            Long cnt   = r[3] != null ? ((Number) r[3]).longValue()   : 0L;
            map.put(bucket, new Stat(avg, std, cnt));
        }

        var items = java.util.stream.IntStream.range(0, 6)
                .mapToObj(b -> {
                    String range = String.format("%02d-%02d", b * 4, b * 4 + 4);
                    Stat s = map.getOrDefault(b, Stat.EMPTY);
                    return new BucketResponse.Item(range, s.avg(), s.std(), s.cnt());
                })
                .toList();

        return new BucketResponse(date, items);
    }

    private record Stat(Double avg, Double std, Long cnt) {
        static final Stat EMPTY = new Stat(null, null, 0L);
    }

    @Transactional(readOnly = true)
    public StepResponse overallCumulativeAvg(Long coupleId) {
        List<Object[]> rows = repo.aggregateStepsOverallCumulative(coupleId);

        List<StepResponse.Item> items = new ArrayList<>(2);
        for (Object[] r : rows) {
            String label = (String) r[1];                       // "00-12" | "00-16"
            Double avg   = (r[2] != null) ? ((Number) r[2]).doubleValue() : null;
            items.add(new StepResponse.Item(label, avg));
        }
        // 혹시 둘 중 일부가 비어도 일관된 순서가 되도록 보정하고 싶다면 여기서 채워 넣어도 됨.
        return new StepResponse(items);
    }

    // ---- mappers ----
    private GetResponse toGet(HealthData hd) {
        return new GetResponse(
                hd.getHealthId(),
                hd.getDate(),
                hd.getStress(),
                hd.getHeartrate()
//                hd.getSteps()
        );
    }

//    private ListResponse.Item toItem(HealthData hd) {
//        return new ListResponse.Item(
//                hd.getHealthId(),
//                hd.getDate(),
//                hd.getStress(),
//                hd.getSleepHours(),
//                hd.getHeartrate(),
//                hd.getSteps(),
//                hd.getIsDanger()
//        );
//    }
}
