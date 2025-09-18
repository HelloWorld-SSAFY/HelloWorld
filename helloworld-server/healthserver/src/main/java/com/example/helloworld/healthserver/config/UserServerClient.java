package com.example.helloworld.healthserver.config;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(
        name = "user-server",                 // 유레카 쓰면 name만 사용
        url = "${userserver.base-url:}",      // 정적 URL이면 여기 세팅
        configuration = FeignAuthConfig.class
)
public interface UserServerClient {

    @GetMapping("/api/users/me")          // 유저서버가 JWT 검증 후 반환
    MeResponse me();

    record MeResponse(Long userId, Long coupleId, String email) {}
}