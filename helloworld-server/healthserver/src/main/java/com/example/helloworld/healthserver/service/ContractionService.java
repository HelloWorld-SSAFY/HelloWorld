package com.example.helloworld.healthserver.service;

import com.example.helloworld.healthserver.dto.request.CsCreateRequest;
import com.example.helloworld.healthserver.dto.response.CsCreateResponse;
import com.example.helloworld.healthserver.dto.response.CsListResponse;
import com.example.helloworld.healthserver.entity.ContractionSession;
import com.example.helloworld.healthserver.persistence.ContractionSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.time.Instant;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class ContractionService {

    private final ContractionSessionRepository repo;

    @Transactional
    public CsCreateResponse create(Long coupleId, CsCreateRequest req) {
        ContractionSession cs = ContractionSession.builder()
                .coupleId(coupleId)
                .startTime(req.start_time())
                .endTime(req.end_time())
                .build();

        // 파생 필드 계산
        cs.fillDerived();
        repo.findTopByCoupleIdOrderByEndTimeDesc(coupleId)
                .map(ContractionSession::getEndTime)
                .ifPresent(cs::setIntervalFromPrev);

        repo.save(cs);
        return new CsCreateResponse(
                "cs_" + cs.getId(),
                cs.getStartTime(),
                cs.getEndTime(),
                cs.getDurationSec(),
                cs.getIntervalMin()
        );
    }

    @Transactional(readOnly = true)
    public CsListResponse list(Long coupleId, Instant from, Instant to) {
        List<ContractionSession> list = (from != null && to != null)
                ? repo.findByCoupleIdAndStartTimeBetweenOrderByStartTimeDesc(coupleId, from, to)
                : repo.findByCoupleIdOrderByStartTimeDesc(coupleId);

        var items = list.stream().map(s ->
                new CsListResponse.CSItem(
                        s.getId(),
                        s.getStartTime(),
                        s.getEndTime(),
                        s.getDurationSec(),
                        s.getIntervalMin(),
                        s.isAlertSent()
                )
        ).toList();
        return new CsListResponse(items);
    }

    @Transactional
    public void markAlertSent(Long coupleId, Long sessionId) {
        var cs = repo.findById(sessionId)
                .filter(s -> s.getCoupleId().equals(coupleId))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Session not found"));
        cs.markAlertSent();
    }
}
