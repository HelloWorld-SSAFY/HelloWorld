package com.example.helloworld.healthserver.service;

import com.example.helloworld.healthserver.dto.MHDtos.*;
import com.example.helloworld.healthserver.entity.MaternalHealth;
import com.example.helloworld.healthserver.persistence.MaternalHealthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class MaternalHealthService {

    private final MaternalHealthRepository repo;

    @Value("${app.zone:Asia/Seoul}")
    private String appZone;

    private static final Pattern BP = Pattern.compile("^(\\d{2,3})/(\\d{2,3})$");

    private BigDecimal safe(BigDecimal bd) {
        return (bd == null) ? null : bd.stripTrailingZeros();
    }

    private String formatBp(Integer max, Integer min) {
        if (max == null || min == null) return null;
        return max + "/" + min;
    }

    private int[] parseBp(String s) {
        if (s == null) return null;
        Matcher m = BP.matcher(s.trim());
        if (!m.matches()) throw new ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "blood_pressure must be 'NNN/NNN'");
        return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
    }

//    @Transactional(readOnly = true)
//    public MhGetResponse getLatest(Long coupleId) {
//        MaternalHealth mh = repo.findTopByCoupleIdOrderByRecordDateDescCreatedAtDesc(coupleId)
//                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No records"));
//        return new MhGetResponse(
//                mh.getRecordDate().toString(),
//                safe(mh.getWeight()),
//                formatBp(mh.getMaxBloodPressure(), mh.getMinBloodPressure()),
//                mh.getBloodSugar()
//        );
//    }

    private com.example.helloworld.healthserver.dto.MHDtos.MhGetResponse toDto(MaternalHealth mh) {
        return new com.example.helloworld.healthserver.dto.MHDtos.MhGetResponse(
                mh.getRecordDate().toString(),
                safe(mh.getWeight()),
                formatBp(mh.getMaxBloodPressure(), mh.getMinBloodPressure()),
                mh.getBloodSugar()
        );
    }

    @Transactional(readOnly = true)
    public com.example.helloworld.healthserver.dto.MHDtos.MhGetResponse getById(Long coupleId, Long maternalId) {
        MaternalHealth mh = repo.findByIdAndCoupleId(maternalId, coupleId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Record not found"));
        return toDto(mh);
    }

    @Transactional
    public void create(Long coupleId, MhCreateRequest req) {
        ZoneId zone = ZoneId.of(appZone);
        LocalDate today = LocalDate.now(zone);
        MaternalHealth mh = MaternalHealth.builder()
                .coupleId(coupleId)
                .recordDate(today)
                .weight(safe(req.weight()))
                .maxBloodPressure(req.max_blood_pressure())
                .minBloodPressure(req.min_blood_pressure())
                .bloodSugar(req.blood_sugar())
                .build();
        repo.save(mh);
    }

    @Transactional
    public MhUpdateResponse update(Long coupleId, Long maternalId, MhUpdateRequest req) {
        MaternalHealth mh = repo.findByIdAndCoupleId(maternalId, coupleId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Record not found"));

        if (req.weight() != null) {
            mh.setWeight(safe(req.weight()));
        }
        if (req.blood_pressure() != null) {
            int[] bp = parseBp(req.blood_pressure());
            mh.setMaxBloodPressure(bp[0]);
            mh.setMinBloodPressure(bp[1]);
        }
        if (req.blood_sugar() != null) {
            mh.setBloodSugar(req.blood_sugar());
        }
        return new MhUpdateResponse("mh_" + mh.getId(), true);
    }

    @Transactional
    public void delete(Long coupleId, Long maternalId) {
        MaternalHealth mh = repo.findByIdAndCoupleId(maternalId, coupleId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Record not found"));
        repo.delete(mh);
    }

    @Transactional(readOnly = true)
    public com.example.helloworld.healthserver.dto.MHDtos.MhListResponse list(
            Long coupleId, LocalDate from, LocalDate to) {

        List<MaternalHealth> rows;
        if (from == null && to == null) {
            rows = repo.findByCoupleIdOrderByRecordDateDescCreatedAtDesc(coupleId);
        } else {
            LocalDate start = (from != null) ? from : LocalDate.of(1970, 1, 1);
            LocalDate end   = (to   != null) ? to   : LocalDate.of(9999, 12, 31);
            rows = repo.findByCoupleIdAndRecordDateBetweenOrderByRecordDateDescCreatedAtDesc(
                    coupleId, start, end);
        }

        var utc = java.time.ZoneOffset.UTC;
        var items = rows.stream().map(mh ->
                new com.example.helloworld.healthserver.dto.MHDtos.MhListResponse.Item(
                        mh.getId(),
                        mh.getRecordDate().toString(),
                        safe(mh.getWeight()),
                        formatBp(mh.getMaxBloodPressure(), mh.getMinBloodPressure()),
                        mh.getBloodSugar(),
                        (mh.getCreatedAt() != null ? mh.getCreatedAt() : java.time.Instant.now()).atOffset(utc)
                )
        ).toList();

        return new com.example.helloworld.healthserver.dto.MHDtos.MhListResponse(items);
    }
}
