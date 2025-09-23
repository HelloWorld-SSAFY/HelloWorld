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



    //
    @Transactional(readOnly = true)
    public GlobalDailyStatsResponse getGlobalDailyStats(LocalDate date) {
        if (date == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date is required (YYYY-MM-DD)");
        }

        ZoneId zone = ZoneId.of(appZone);
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();

        List<HealthDataRepository.GlobalDailyBucketStats> results = repo.aggregateGlobalDailyBuckets(from, to);

        List<StatsRow> finalRows = new ArrayList<>();
        for (HealthDataRepository.GlobalDailyBucketStats row : results) {
            String userRef = "c" + row.getCoupleId();

            // 1. Heart Rate - Average
            finalRows.add(new StatsRow(
                    userRef, date, "hr", "avg",
                    row.getAvgHr0(), row.getAvgHr1(), row.getAvgHr2(),
                    row.getAvgHr3(), row.getAvgHr4(), row.getAvgHr5()
            ));
            // 2. Heart Rate - Standard Deviation
            finalRows.add(new StatsRow(
                    userRef, date, "hr", "stddev",
                    row.getStdHr0(), row.getStdHr1(), row.getStdHr2(),
                    row.getStdHr3(), row.getStdHr4(), row.getStdHr5()
            ));
            // 3. Stress - Average
            finalRows.add(new StatsRow(
                    userRef, date, "stress", "avg",
                    row.getAvgSt0(), row.getAvgSt1(), row.getAvgSt2(),
                    row.getAvgSt3(), row.getAvgSt4(), row.getAvgSt5()
            ));
            // 4. Stress - Standard Deviation
            finalRows.add(new StatsRow(
                    userRef, date, "stress", "stddev",
                    row.getStdSt0(), row.getStdSt1(), row.getStdSt2(),
                    row.getStdSt3(), row.getStdSt4(), row.getStdSt5()
            ));
        }

        return new GlobalDailyStatsResponse(finalRows);
    }




    private record Stat(Double avg, Double std, Long cnt) {
        static final Stat EMPTY = new Stat(null, null, 0L);
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
