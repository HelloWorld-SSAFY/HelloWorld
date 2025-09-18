package com.example.helloworld.userserver.member.service;

import com.example.helloworld.userserver.member.dto.request.CoupleUpdateRequest;
import com.example.helloworld.userserver.member.dto.request.MemberRegisterRequest;
import com.example.helloworld.userserver.member.dto.response.MemberProfileResponse;
import com.example.helloworld.userserver.member.dto.request.AvatarUrlRequest;
import com.example.helloworld.userserver.member.dto.response.AvatarUrlResponse;

public interface MemberService {

    Long registerAndUpsertCouple(Long memberId, MemberRegisterRequest req);

    MemberProfileResponse getMyOverview(Long memberId);

    AvatarUrlResponse setAvatarUrl(Long memberId, AvatarUrlRequest req);

    Long updateProfile(Long memberId, MemberProfileResponse.MemberUpdateRequest req);
    Long updateCoupleSharing(Long memberId, CoupleUpdateRequest req);
}
