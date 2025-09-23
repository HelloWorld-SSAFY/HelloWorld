package com.example.helloworld.healthserver.service;

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
}