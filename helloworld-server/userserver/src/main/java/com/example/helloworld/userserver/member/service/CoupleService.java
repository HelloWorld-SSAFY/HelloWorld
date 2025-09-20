package com.example.helloworld.userserver.member.service;


import com.example.helloworld.userserver.member.dto.request.CoupleCreateRequest;
import com.example.helloworld.userserver.member.dto.request.CoupleUpdateRequest;
import com.example.helloworld.userserver.member.dto.response.CoupleResponse;

public interface CoupleService {

    /** 내가 속한 커플 조회 (없으면 404 또는 200 null-정책 중 택1: 여기선 404) */
    CoupleResponse getMyCouple(Long memberId);

    /** 특정 커플 조회 */
    CoupleResponse getById(Long coupleId);

    /** 커플 생성 (여성만 허용, userA=본인, 1인 1커플 정책이면 중복 시 409) */
    CoupleResponse createByFemale(Long memberId, CoupleCreateRequest req);

    /** 커플 공유 정보 수정 (기본: userA만 허용) */
    CoupleResponse updateMyCouple(Long memberId, CoupleUpdateRequest req);
}
