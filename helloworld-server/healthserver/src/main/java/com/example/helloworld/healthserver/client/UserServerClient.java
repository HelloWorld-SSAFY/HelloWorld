package com.example.helloworld.healthserver.client;

import com.example.helloworld.healthserver.config.FeignAuthConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(
        name = "user-server",
        url = "${userserver.base-url}",
        configuration = FeignAuthConfig.class
)
public interface UserServerClient {

    /**
     * 내 커플 상세 정보 조회 (user-server API)
     * 이 API는 요청을 보내는 사용자의 토큰을 기반으로 커플 정보를 반환합니다.
     */
    @GetMapping("/api/couples/me/detail")
    CoupleDetailResponse getCoupleDetail();


    // ✨ 사용자의 FCM 토큰 목록을 조회하는 API (user-server에 구현 필요)
    @GetMapping("/api/internal/users/{userId}/fcm-tokens")
    FcmTokenResponse getFcmTokens(@PathVariable("userId") Long userId);



    record CoupleDetailResponse(CoupleInfo couple, UserInfo userA, UserInfo userB) {}

    record CoupleInfo(long couple_id, Long user_a_id, Long user_b_id) {}

    record UserInfo(long id, String nickname) {}

    record FcmTokenResponse(List<String> tokens) {}
}
