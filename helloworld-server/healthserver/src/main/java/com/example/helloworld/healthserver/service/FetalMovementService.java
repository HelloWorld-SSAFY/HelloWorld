package com.example.helloworld.healthserver.service;

import com.example.helloworld.healthserver.dto.request.FmCreateRequest;
import com.example.helloworld.healthserver.dto.response.FmCreateResponse;
import com.example.helloworld.healthserver.dto.response.FmListResponse;
import com.example.helloworld.healthserver.entity.FetalMovement;
import com.example.helloworld.healthserver.persistence.FetalMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FetalMovementService {

    private final FetalMovementRepository repo;

    @Value("${app.zone:Asia/Seoul}")
    private String appZone;

    @Transactional
    public FmCreateResponse create(Long coupleId, FmCreateRequest req) {
        Instant at = (req.recorded_at() != null) ? req.recorded_at() : Instant.now();
        FetalMovement fm = FetalMovement.builder()
                .coupleId(coupleId)
                .recordedAt(at)
                .notes(req.notes())
                .build();
        repo.save(fm);
        return new FmCreateResponse("fm_" + fm.getId(), fm.getRecordedAt());
    }

    @Transactional(readOnly = true)
    public FmListResponse listDaily(Long coupleId, LocalDate from, LocalDate to) {
        ZoneId zone = ZoneId.of(appZone);
        // 기간 해석
        LocalDate startDate = (from != null) ? from : LocalDate.now(zone).minusDays(30);
        LocalDate endDate   = (to   != null) ? to   : LocalDate.now(zone);
        Instant start = startDate.atStartOfDay(zone).toInstant();
        Instant end   = endDate.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1);

        var items = repo.findByCoupleIdAndRecordedAtBetweenOrderByRecordedAtDesc(coupleId, start, end)
                .stream()
                .collect(Collectors.groupingBy(
                        e -> e.getRecordedAt().atZone(zone).toLocalDate(),
                        Collectors.counting()
                ))
                .entrySet().stream()
                .sorted(Map.Entry.<LocalDate, Long>comparingByKey().reversed())
                .map(e -> {
                    // 일자 00:00 (app zone)을 UTC Instant로
                    Instant dayStartUtc = e.getKey().atStartOfDay(zone).toInstant();
                    return new FmListResponse.Item(dayStartUtc, e.getValue().intValue());
                })
                .toList();

        return new FmListResponse(items);
    }
}

