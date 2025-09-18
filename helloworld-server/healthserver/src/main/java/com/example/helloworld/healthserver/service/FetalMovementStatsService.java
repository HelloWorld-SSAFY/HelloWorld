package com.example.helloworld.healthserver.service;


import com.example.helloworld.healthserver.dto.response.FmStatsResponse;
import com.example.helloworld.healthserver.persistence.FetalMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FetalMovementStatsService {
    private final FetalMovementRepository repo;

    @Transactional(readOnly = true)
    public FmStatsResponse daily(Long diaryId, LocalDate from, LocalDate to) {
        Long coupleId = diaryId;

        LocalDate start = (from != null) ? from : LocalDate.now(ZoneOffset.UTC).minusDays(13);
        LocalDate end   = (to   != null) ? to   : LocalDate.now(ZoneOffset.UTC);
        if (start.isAfter(end)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from > to");

        Instant fromI = start.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toI   = end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<Object[]> rows = repo.aggregateByDay(coupleId, fromI, toI);
        List<FmStatsResponse.Item> items = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            java.sql.Date day = (java.sql.Date) r[0];
            long total = ((Number) r[1]).longValue();
            items.add(new FmStatsResponse.Item(day.toLocalDate(), total));
        }
        return new FmStatsResponse(items);
    }
}