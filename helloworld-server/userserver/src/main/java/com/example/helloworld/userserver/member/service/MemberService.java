package com.example.helloworld.userserver.member.service;

import com.example.helloworld.userserver.member.dto.request.CoupleUpdateRequest;
import com.example.helloworld.userserver.member.dto.request.MemberRegisterRequest;
import com.example.helloworld.userserver.member.dto.response.MemberProfileResponse;
import com.example.helloworld.userserver.member.dto.request.AvatarUrlRequest;
import com.example.helloworld.userserver.member.dto.response.AvatarUrlResponse;

public interface MemberService {

    /** 회원 등록/갱신 (회원 필드만) */
    void register(Long memberId, MemberRegisterRequest req);

    /** 내 프로필 조회 (회원만) */
    MemberProfileResponse getMe(Long memberId);

    /** 내 프로필 부분 수정 (null은 유지) */
    void updateProfile(Long memberId, MemberProfileResponse.MemberUpdateRequest req);

    /** 프로필 이미지 URL 설정/해제 */
    AvatarUrlResponse setAvatarUrl(Long memberId, AvatarUrlRequest req);
}
