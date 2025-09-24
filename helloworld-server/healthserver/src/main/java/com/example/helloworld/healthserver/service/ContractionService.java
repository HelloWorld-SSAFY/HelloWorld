package com.example.helloworld.healthserver.service;

import com.example.helloworld.healthserver.dto.request.CsCreateRequest;
import com.example.helloworld.healthserver.dto.response.CsCreateResponse;
import com.example.helloworld.healthserver.dto.response.CsListResponse;
import com.example.helloworld.healthserver.entity.ContractionSession;
import com.example.helloworld.healthserver.persistence.ContractionSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.time.Instant;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class ContractionService {

    private final ContractionSessionRepository repo;

    @Value("${app.zone:Asia/Seoul}")
    private String appZone;

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
    public CsListResponse list(Long coupleId, LocalDate from, LocalDate to) {
        ZoneId zone = ZoneId.of(appZone);

        // 기본 구간(미지정 시 전기간)
        LocalDate startDate = (from != null) ? from : LocalDate.of(1970, 1, 1);
        LocalDate endDate   = (to   != null) ? to   : LocalDate.of(9999, 12, 31);

        // [start, end) 형태: 시작일 00:00 ~ 종료일 다음날 00:00
        Instant start = startDate.atStartOfDay(zone).toInstant();
        Instant end   = endDate.plusDays(1).atStartOfDay(zone).toInstant();

        List<ContractionSession> list =
                repo.findByCoupleIdAndStartTimeBetweenOrderByStartTimeDesc(coupleId, start, end);

        var items = list.stream().map(s ->
                new CsListResponse.CSItem(
                        s.getId(),
                        s.getStartTime(),   // Instant라면 그대로 반환(클라 포맷은 컨트롤러/DTO에서)
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
