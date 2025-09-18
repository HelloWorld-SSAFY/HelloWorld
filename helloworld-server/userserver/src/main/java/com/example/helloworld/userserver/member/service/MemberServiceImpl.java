package com.example.helloworld.userserver.member.service;

import com.example.helloworld.userserver.member.dto.request.AvatarUrlRequest;
import com.example.helloworld.userserver.member.dto.request.CoupleUpdateRequest;
import com.example.helloworld.userserver.member.dto.response.AvatarUrlResponse;
import com.example.helloworld.userserver.member.dto.response.MemberProfileResponse;
import com.example.helloworld.userserver.member.dto.request.MemberRegisterRequest;
import com.example.helloworld.userserver.member.entity.Couple;
import com.example.helloworld.userserver.member.entity.Member;
import com.example.helloworld.userserver.member.persistence.CoupleRepository;
import com.example.helloworld.userserver.member.persistence.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.Locale;

import java.util.Optional;

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

    @Transactional
    @Override
    public Long updateProfile(Long memberId, MemberProfileResponse.MemberUpdateRequest req) {
        Member me = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        // 닉네임 중복 검사
        if (req.nickname() != null &&
                memberRepository.existsByNicknameAndIdNot(req.nickname(), memberId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "닉네임 중복");
        }

        // 부분 업데이트: null이면 기존 값 유지
        String nickname = (req.nickname() != null) ? req.nickname() : me.getNickname();
        java.time.LocalDate menstrualDate = (req.menstrualDate() != null) ? req.menstrualDate() : me.getMenstrualDate();
        Boolean isChildbirth = (req.isChildbirth() != null) ? req.isChildbirth() : me.isChildbirth();
        Integer age = (req.age() != null) ? req.age() : me.getAge();

        me.applyRegistration(nickname, me.getGender(), menstrualDate, isChildbirth, age);

        // 내 커플 id 반환(없으면 null)
        return coupleRepository.findByUserAId(me.getId())
                .map(Couple::getId)
                .orElse(null);
    }

    @Transactional
    @Override
    public Long updateCoupleSharing(Long memberId, CoupleUpdateRequest req) {
        Member me = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        // 기본 정책: 여성(userA)만 수정 허용
        Couple couple = coupleRepository.findByUserAId(me.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "커플이 없거나 수정 권한 없음"));

        Integer week = (req.pregnancyWeek() != null) ? req.pregnancyWeek() : couple.getPregnancyWeek();
        java.sql.Timestamp due = (req.dueDate() != null) ? toTimestamp(req.dueDate()) : couple.getDueDate();

        couple.updateSharing(week, due);
        return couple.getId();
    }

    @Transactional(readOnly = true)
    @Override
    public MemberProfileResponse getMyOverview(Long memberId) {
        Member me = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        // 내가 속한 커플 찾기: 우선 A(여성), 없으면 B(남성)
        Optional<Couple> asA = coupleRepository.findByUserAId(me.getId());
        Optional<Couple> asB = coupleRepository.findByUserBId(me.getId());
        Couple couple = asA.or(() -> asB).orElse(null);

        // member block
        MemberProfileResponse.MemberBlock memberBlock = new MemberProfileResponse.MemberBlock(
                me.getId(),
                me.getGoogleEmail(),
                me.getNickname(),
                me.getGender() != null ? me.getGender().name().toLowerCase() : null,
                me.getAge(),
                me.getMenstrualDate(),
                me.isChildbirth(),
                me.getImageUrl()
        );

        // couple block (없을 수 있음)
        MemberProfileResponse.CoupleBlock coupleBlock = null;
        if (couple != null) {
            Long userAId = (couple.getUserA() != null) ? couple.getUserA().getId() : null;
            Long userBId = (couple.getUserB() != null) ? couple.getUserB().getId() : null;

            // couple.getDueDate() 타입에 맞춰 변환 (Timestamp라면 toLocalDate 사용, 이미 LocalDate면 그대로)
            LocalDate dueDate = MemberProfileResponse.toLocalDate(couple.getDueDate());

            coupleBlock = new MemberProfileResponse.CoupleBlock(
                    couple.getId(),
                    userAId,
                    userBId,
                    couple.getPregnancyWeek(),
                    dueDate
            );
        }

        return new MemberProfileResponse(memberBlock, coupleBlock);
    }

    @Transactional
    @Override
    public AvatarUrlResponse setAvatarUrl(Long memberId, AvatarUrlRequest req) {
        Member me = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        String url = normalizeUrl(req.imageUrl()); // 빈 값 허용(해제)
        // (선택) URL 필수로 강제하고 싶으면 빈 값이면 400 던지면 됨

        me.updateImageUrl(url);
        return new AvatarUrlResponse(me.getImageUrl() == null ? "" : me.getImageUrl());
    }

    private static String normalizeUrl(String s) {
        if (s == null || s.isBlank()) return ""; // 비우기(해제) 허용
        try {
            URI u = new URI(s.trim());
            String scheme = (u.getScheme() == null) ? "" : u.getScheme().toLowerCase();
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image_url must be http/https");
            }
            return u.toString();
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid image_url");
        }
    }

    private static Member.Gender toGender(String s) {
        if (s == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "gender is required");
        }
        String v = s.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "female" -> Member.Gender.FEMALE;
            case "male" -> Member.Gender.MALE;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid gender: " + s);
        };
    }

    private static java.sql.Timestamp toTimestamp(java.time.LocalDate d) {
        return (d == null) ? null : java.sql.Timestamp.valueOf(d.atStartOfDay());
    }
}

