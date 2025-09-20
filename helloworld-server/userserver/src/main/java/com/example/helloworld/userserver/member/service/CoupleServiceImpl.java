package com.example.helloworld.userserver.member.service;

import com.example.helloworld.userserver.member.dto.request.CoupleCreateRequest;
import com.example.helloworld.userserver.member.dto.request.CoupleUpdateRequest;
import com.example.helloworld.userserver.member.dto.response.CoupleResponse;
import com.example.helloworld.userserver.member.entity.Couple;
import com.example.helloworld.userserver.member.entity.Member;
import com.example.helloworld.userserver.member.persistence.CoupleRepository;
import com.example.helloworld.userserver.member.persistence.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class CoupleServiceImpl implements CoupleService {

    private final MemberRepository memberRepository;
    private final CoupleRepository coupleRepository;

    @Transactional(readOnly = true)
    @Override
    public CoupleResponse getMyCouple(Long memberId) {
        Member me = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        Couple couple = coupleRepository.findByUserAId(me.getId())
                .or(() -> coupleRepository.findByUserBId(me.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Couple not found"));

        return toResponse(couple);
    }

    @Transactional(readOnly = true)
    @Override
    public CoupleResponse getById(Long coupleId) {
        Couple couple = coupleRepository.findById(coupleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Couple not found"));
        // 접근 제어가 필요하면 여기서 수행
        return toResponse(couple);
    }

    @Transactional
    @Override
    public CoupleResponse createByFemale(Long memberId, CoupleCreateRequest req) {
        Member me = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        if (me.getGender() != Member.Gender.FEMALE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "여성만 커플 생성 가능");
        }

        // 한 여성당 1커플 정책이라면
        coupleRepository.findByUserAId(me.getId()).ifPresent(c -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 커플이 존재합니다");
        });

        Couple couple = Couple.builder()
                .userA(me)
                .userB(null)
                .pregnancyWeek(req.pregnancyWeek())
                .dueDate(toTimestamp(req.dueDate()))
                .menstrualDate(req.menstrualDate())
                .isChildbirth(Boolean.TRUE.equals(req.isChildbirth()))
                .build();

        coupleRepository.save(couple);
        return toResponse(couple);
    }

    @Transactional
    @Override
    public CoupleResponse updateMyCouple(Long memberId, CoupleUpdateRequest req) {
        Member me = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        Couple couple = coupleRepository.findByUserAId(me.getId())
                .or(() -> coupleRepository.findByUserBId(me.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "커플을 찾을 수 없음"));

        // 정책: 여성(userA)만 수정 가능하게 제한하려면 여기서 체크
        if (couple.getUserA() == null || !couple.getUserA().getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "수정 권한 없음");
        }

        Integer week       = (req.pregnancyWeek() != null) ? req.pregnancyWeek() : couple.getPregnancyWeek();
        Timestamp due      = (req.dueDate() != null) ? toTimestamp(req.dueDate()) : couple.getDueDate();
        LocalDate menstrual= (req.menstrualDate() != null) ? req.menstrualDate() : couple.getMenstrualDate();
        Boolean childbirth = (req.isChildbirth() != null) ? req.isChildbirth() : couple.isChildbirth();

        couple.updateSharing(week, due, menstrual, childbirth);
        return toResponse(couple);
    }
    /** LocalDate(yyyy-MM-dd)를 자정 기준 Timestamp로 변환 (null-safe) */
    private static Timestamp toTimestamp(LocalDate d) {
        return (d == null) ? null : Timestamp.valueOf(d.atStartOfDay());
    }

    /** 엔티티 → 응답 DTO 매핑 (null-safe 변환 포함) */
    private static CoupleResponse toResponse(Couple c) {
        return new CoupleResponse(
                c.getId(),
                (c.getUserA() != null) ? c.getUserA().getId() : null,
                (c.getUserB() != null) ? c.getUserB().getId() : null,
                c.getPregnancyWeek(),
                (c.getDueDate() != null) ? c.getDueDate().toLocalDateTime().toLocalDate() : null,
                c.getMenstrualDate(),
                c.isChildbirth()
        );
    }
}
