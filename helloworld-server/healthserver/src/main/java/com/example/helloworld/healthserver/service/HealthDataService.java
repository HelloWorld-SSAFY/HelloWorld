package com.example.helloworld.healthserver.service;

import com.example.helloworld.healthserver.client.AiServerClient;
import com.example.helloworld.healthserver.alarm.service.FcmService;
import com.example.helloworld.healthserver.config.UserPrincipal;
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
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class HealthDataService {

    private final HealthDataRepository repo;

    private final AiServerClient aiServerClient;
    private final FcmService fcmService;

    @Value("${app.zone:Asia/Seoul}")
    private String appZone;

    /** 프론트 /api/wearable 진입점: DB 저장 후 AI 호출, AI 응답을 그대로 리턴 */
    private static final Set<String> ANOMALY_MODES = Set.of("RESTRICT", "EMERGENCY");

    @Transactional
    public AiServerClient.AnomalyResponse createAndCheckHealthData(UserPrincipal user, HealthDtos.CreateRequest req) {
        // 1. DB에 데이터 저장
        Instant timestamp = req.date() != null ? req.date() : Instant.now();
        HealthData healthData = HealthData.builder()
                .coupleId(user.getCoupleId())
                .date(timestamp)
                .stress(req.stress())
                .heartrate(req.heartrate())
                .build();
        repo.save(healthData);

        // 2. AI 서버에 분석 요청
        String userRef = "u" + user.getUserId();
        String isoTimestamp = timestamp.atZone(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        AiServerClient.Metrics metrics = new AiServerClient.Metrics(req.heartrate(), req.stress());
        AiServerClient.TelemetryRequest telemetryRequest = new AiServerClient.TelemetryRequest(userRef, isoTimestamp, metrics);

        AiServerClient.AnomalyResponse anomalyResponse = aiServerClient.checkTelemetry(telemetryRequest);

        // 3. 이상 징후 시 비동기 FCM 알림 전송
        if (anomalyResponse != null && ANOMALY_MODES.contains(anomalyResponse.mode().toUpperCase())) {
            //fcmService.sendEmergencyNotification(user.getUserId(), user.getCoupleId(), req.heartrate());
            fcmService.sendEmergencyNotification(user.getUserId(), req.heartrate());
        }

        // 4. AI 서버 응답 반환
        return anomalyResponse;
    }

    // ✨ 보안 강화 예시: coupleId를 파라미터로 받아 소유권 검증
//    @Transactional(readOnly = true)
//    public HealthDtos.GetResponse getById(Long coupleId, Long healthId) {
//        var hd = repo.findByHealthIdAndCoupleId(healthId, coupleId)
//                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found"));
//        return new HealthDtos.GetResponse(hd.getHealthId(), hd.getDate(), hd.getStress(), hd.getHeartrate());
//    }


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
