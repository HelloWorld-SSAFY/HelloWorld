package com.example.helloworld.userserver.member.service;

import com.example.helloworld.userserver.member.dto.MemberRegisterRequest;

public interface MemberService {

    Long registerAndUpsertCouple(Long memberId, MemberRegisterRequest req);
}
