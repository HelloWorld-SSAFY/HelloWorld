package com.example.helloworld.healthserver.service;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StepsDataService {

    private final StepsDataRepository repo;

    @Value("${app.zone:Asia/Seoul}")
    private String appZone;

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
}