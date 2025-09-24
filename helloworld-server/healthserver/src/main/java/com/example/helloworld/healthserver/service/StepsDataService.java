package com.example.helloworld.healthserver.service;

import com.example.helloworld.healthserver.client.AiServerClient;
import com.example.helloworld.healthserver.config.UserPrincipal;
import com.example.helloworld.healthserver.dto.StepsDtos;
import com.example.helloworld.healthserver.dto.StepsDtos.CreateRequest;
import com.example.helloworld.healthserver.dto.StepsDtos.CreateResponse;
import com.example.helloworld.healthserver.entity.StepsData;
import com.example.helloworld.healthserver.persistence.StepsDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StepsDataService {

    private final StepsDataRepository repo;

    private final AiServerClient aiServerClient;

    @Value("${app.zone:Asia/Seoul}")
    private String appZone;

    @Transactional
    public StepsDtos.CreateWithAnomalyResponse createAndCheck(UserPrincipal user, CreateRequest req) {
        // 1) 저장
        StepsDtos.CreateResponse stepsResponse = create(user.getCoupleId(), req);

        // 2) AI 서버 호출
        AiServerClient.StepsCheckResponse aiResp = checkStepsAnomaly(user, req);

        // 2-1) recommendation 매핑 (Client -> DTO)
        StepsDtos.CreateWithAnomalyResponse.StepsRecommendation recDto = null;
        if (aiResp != null && aiResp.recommendation() != null) {
            var rec = aiResp.recommendation();
            var catDtos = Optional.ofNullable(rec.categories()).orElse(List.of())
                    .stream()
                    .map(c -> new StepsDtos.CreateWithAnomalyResponse.StepsCategory(
                            c.category(), c.rank(), c.reason()
                    ))
                    .toList();
            recDto = new StepsDtos.CreateWithAnomalyResponse.StepsRecommendation(rec.sessionId(), catDtos);
        }

        // 3) 통합 응답
        return new StepsDtos.CreateWithAnomalyResponse(
                stepsResponse.stepsId(),
                stepsResponse.date(),
                stepsResponse.steps(),
                aiResp != null && aiResp.ok(),
                aiResp != null && aiResp.anomaly(),
                aiResp != null ? aiResp.mode() : null,
                aiResp != null ? aiResp.trigger() : null,
                aiResp != null ? aiResp.reasons() : null,
                recDto
        );
    }

    @Transactional
    public CreateResponse create(Long coupleId, CreateRequest req) {
        log.info("StepsDataService.create - coupleId: {}, req: {}", coupleId, req);

        if (coupleId == null || coupleId <= 0) {
            log.error("Invalid couple_id: {}", coupleId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid couple_id");
        }


        if (req == null || req.steps() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "steps is required");
        }

        // date 없으면 now(서버 UTC)로 저장. 클라이언트가 로컬 시간을 보낼 땐 백엔드에서 UTC Instant 변환 권장.
        Instant when = Optional.ofNullable(req.date()).orElse(Instant.now());

        StepsData row = StepsData.builder()
                .coupleId(coupleId)
                .date(when)
                .steps(req.steps())
                .latitude(req.latitude())
                .longitude(req.longitude())
                .build();

        row = repo.save(row);
        log.info("Steps saved with id: {}", row.getStepsId());
        return new CreateResponse(row.getStepsId(), row.getDate(), row.getSteps());
    }

    @Transactional(readOnly = true)
    public CreateResponse getById(Long coupleId, Long stepsId) {
        StepsData row = repo.findByStepsIdAndCoupleId(stepsId, coupleId);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found");
        }
        return new CreateResponse(row.getStepsId(), row.getDate(), row.getSteps());
    }

    @Transactional(readOnly = true)
    public StepsDtos.StepResponse overallCumulativeAvg(Long coupleId) {
        List<Object[]> rows = repo.aggregateStepsOverallCumulative(coupleId);

        List<StepsDtos.StepResponse.Item> items = new ArrayList<>(2);
        for (Object[] r : rows) {
            String label = (String) r[1];                       // "00-12" | "00-16"
            Double avg   = (r[2] != null) ? ((Number) r[2]).doubleValue() : null;
            items.add(new StepsDtos.StepResponse.Item(label, avg));
        }
        // 혹시 둘 중 일부가 비어도 일관된 순서가 되도록 보정하고 싶다면 여기서 채워 넣어도 됨.
        return new StepsDtos.StepResponse(items);
    }


    private Integer resolveAvgStepsForNow(Long coupleId, Instant ts) {
        // 1) 현재 시간이 어느 버킷인지 결정 (경계 포함)
        ZoneId zone = ZoneId.of(appZone);
        int hour = ts.atZone(zone).getHour();
        String wantedLabel = (hour <= 12) ? "00-12" : (hour <= 16) ? "00-16" : "00-24";

        // 2) 평균 테이블 조회 (00-12, 00-16만 옴)
        StepsDtos.StepResponse resp = overallCumulativeAvg(coupleId);

        // 3) 매칭되는 라벨의 평균 추출 (00-24는 없으면 null → AI 폴백)
        return resp.records().stream()
                .filter(it -> wantedLabel.equals(it.hourRange()))
                .map(StepsDtos.StepResponse.Item::avgSteps)   // Double
                .filter(Objects::nonNull)
                .map(d -> (int) Math.round(d))                // 반올림 int
                .findFirst()
                .orElse(null);
    }

    private AiServerClient.StepsCheckResponse checkStepsAnomaly(UserPrincipal user, StepsDtos.CreateRequest req) {
        if (user == null || user.getCoupleId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not associated with a couple.");
        }

        Instant ts = (req.date() != null) ? req.date() : Instant.now();
        String tsIso = ts.atZone(ZoneId.of(appZone)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        // ★ 여기서 평균 계산해서 넣어줌
        Integer avgForNow = resolveAvgStepsForNow(user.getCoupleId(), ts);

        var body = new AiServerClient.StepsCheckRequest(
                tsIso,
                req.steps(),     // cum_steps
                avgForNow,       // avg_steps (없으면 null 보내서 AI 폴백)
                req.latitude(),  // lat
                req.longitude()  // lng
        );

        try {
            return aiServerClient.checkSteps(user.getCoupleId(), body);
        } catch (feign.FeignException.Unauthorized e) {
            log.error("AI steps-check 401 Unauthorized: {}", e.contentUTF8());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI server unauthorized");
        } catch (feign.FeignException e) {
            log.error("AI steps-check error status={}, body={}", e.status(), e.contentUTF8());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI server error");
        }
    }

}