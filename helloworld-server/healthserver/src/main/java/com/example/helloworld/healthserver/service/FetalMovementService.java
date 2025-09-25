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
        ZoneId zone = ZoneId.of(appZone);

        Instant at;
        if (req.recorded_at() != null) {
            // 클라이언트가 보낸 시간 사용
            at = req.recorded_at();
        } else {
            // 서울 기준 현재 시간 생성
            LocalDateTime nowInSeoul = LocalDateTime.now(zone);
            at = nowInSeoul.atZone(zone).toInstant();
        }

        FetalMovement fm = FetalMovement.builder()
                .coupleId(coupleId)
                .recordedAt(at)
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
                        Collectors.toList() // counting() 대신 toList()로 변경
                ))
                .entrySet().stream()
                .sorted(Map.Entry.<LocalDate, List<FetalMovement>>comparingByKey().reversed())
                .map(e -> {
                    // 날짜 자체를 반환 (LocalDate)
                    LocalDate recordedDate = e.getKey();

                    return new FmListResponse.FmListItem(recordedDate, e.getValue().size());
                })
                .toList();

        return new FmListResponse(items);
    }
}