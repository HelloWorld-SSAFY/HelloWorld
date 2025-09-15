package com.example.helloworld.userserver.member.service;

import com.example.helloworld.userserver.member.dto.CoupleUpdateRequest;
import com.example.helloworld.userserver.member.dto.MemberRegisterRequest;
import com.example.helloworld.userserver.member.dto.MemberUpdateRequest;

public interface MemberService {

    Long registerAndUpsertCouple(Long memberId, MemberRegisterRequest req);
    Long updateProfile(Long memberId, MemberUpdateRequest req);
    Long updateCoupleSharing(Long memberId, CoupleUpdateRequest req);
}
