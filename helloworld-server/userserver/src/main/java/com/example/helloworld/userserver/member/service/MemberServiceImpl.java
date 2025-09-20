package com.example.helloworld.userserver.member.service;

import com.example.helloworld.userserver.member.dto.request.AvatarUrlRequest;
import com.example.helloworld.userserver.member.dto.request.MemberRegisterRequest;
import com.example.helloworld.userserver.member.dto.response.AvatarUrlResponse;
import com.example.helloworld.userserver.member.dto.response.MemberProfileResponse;
import com.example.helloworld.userserver.member.entity.Member;
import com.example.helloworld.userserver.member.persistence.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final MemberRepository memberRepository;

    @Transactional
    @Override
    public void register(Long memberId, MemberRegisterRequest req) {
        // 닉네임 중복(자기 자신 제외)
        if (req.nickname() != null &&
                memberRepository.existsByNicknameAndIdNot(req.nickname(), memberId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "닉네임 중복");
        }

        Member me = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        me.ensureImageUrlNotNull();

        Member.Gender genderEnum = toGender(req.gender());
        me.applyRegistration(req.nickname(), genderEnum, req.age());
        // flush는 트랜잭션 종료 시
    }

    @Transactional(readOnly = true)
    @Override
    public MemberProfileResponse getMe(Long memberId) {
        Member me = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        MemberProfileResponse.MemberBlock memberBlock = new MemberProfileResponse.MemberBlock(
                me.getId(),
                me.getGoogleEmail(),
                me.getNickname(),
                me.getGender() != null ? me.getGender().name().toLowerCase() : null,
                me.getAge(),
                me.getImageUrl()
        );
        return new MemberProfileResponse(memberBlock);
    }

    @Transactional
    @Override
    public void updateProfile(Long memberId, MemberProfileResponse.MemberUpdateRequest req) {
        Member me = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        if (req.nickname() != null &&
                memberRepository.existsByNicknameAndIdNot(req.nickname(), memberId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "닉네임 중복");
        }

        String nickname = (req.nickname() != null) ? req.nickname() : me.getNickname();
        Integer age = (req.age() != null) ? req.age() : me.getAge();
        me.applyRegistration(nickname, me.getGender(), age);
    }

    @Transactional
    @Override
    public AvatarUrlResponse setAvatarUrl(Long memberId, AvatarUrlRequest req) {
        Member me = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        String url = normalizeUrl(req.imageUrl()); // 빈 값 → 해제
        me.updateImageUrl(url);
        return new AvatarUrlResponse(me.getImageUrl() == null ? "" : me.getImageUrl());
    }

    // helpers
    private static Member.Gender toGender(String s) {
        if (s == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "gender is required");
        String v = s.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (v) {
            case "female" -> Member.Gender.FEMALE;
            case "male" -> Member.Gender.MALE;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid gender: " + s);
        };
    }

    private static String normalizeUrl(String s) {
        if (s == null || s.isBlank()) return "";
        try {
            var u = new java.net.URI(s.trim());
            String scheme = (u.getScheme() == null) ? "" : u.getScheme().toLowerCase();
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image_url must be http/https");
            }
            return u.toString();
        } catch (java.net.URISyntaxException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid image_url");
        }
    }
}