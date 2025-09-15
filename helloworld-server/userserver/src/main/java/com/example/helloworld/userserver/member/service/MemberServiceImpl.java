package com.example.helloworld.userserver.member.service;

import com.example.helloworld.userserver.member.dto.MemberRegisterRequest;
import com.example.helloworld.userserver.member.entity.Couple;
import com.example.helloworld.userserver.member.entity.Member;
import com.example.helloworld.userserver.member.persistence.CoupleRepository;
import com.example.helloworld.userserver.member.persistence.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Locale;

import java.time.LocalDate;
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {
    private final MemberRepository memberRepository;
    private final CoupleRepository coupleRepository;

    @Transactional
    @Override
    public Long registerAndUpsertCouple(Long memberId, MemberRegisterRequest req) {
        // 1) 닉네임 중복(자기 자신 제외)
        if (req.nickname() != null &&
                memberRepository.existsByNicknameAndIdNot(req.nickname(), memberId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "닉네임 중복");
        }

        // 2) 내 프로필 업서트
        Member me = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        me.ensureImageUrlNotNull();

        Member.Gender genderEnum = toGender(req.gender());

        me.applyRegistration(
                req.nickname(),
                genderEnum,         // "female" | "male" (소문자 문자열 유지)
                req.menstrualDate(),   // null 허용
                req.isChildbirth(),
                req.age()
        );

        // 3) 성별이 female이면 커플 자동 생성/재사용, 아니면 커플ID는 null 반환
        Long coupleId = null;
        if (genderEnum == Member.Gender.FEMALE) {
            // 내가 A로 들어간 커플이 이미 있는지 체크(중복 생성 방지)
            Couple couple = coupleRepository.findByUserAId(me.getId()).orElse(null);
            if (couple == null) {
                couple = Couple.builder()
                        .userA(me)
                        .userB(null)
                        .pregnancyWeek(req.pregnancyWeek())                      // ✅ 반영
                        .dueDate(toTimestamp(req.dueDate()))
                        .build();
                coupleRepository.save(couple);
            }
            coupleId = couple.getId();
        }

        // 영속성 컨텍스트 flush 시점에 자동 저장
        return coupleId;
    }
    private static Member.Gender toGender(String s) {
        if (s == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "gender is required");
        }
        String v = s.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "female" -> Member.Gender.FEMALE;
            case "male"   -> Member.Gender.MALE;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid gender: " + s);
        };
    }

    private static java.sql.Timestamp toTimestamp(java.time.LocalDate d) {
        return (d == null) ? null : java.sql.Timestamp.valueOf(d.atStartOfDay());
    }
}
