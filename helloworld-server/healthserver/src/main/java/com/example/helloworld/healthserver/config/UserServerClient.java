package com.example.helloworld.healthserver.config;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(
        name = "user-server",                 // Eureka 쓰면 name만
        url = "${userserver.base-url:}",      // 정적 주소면 이 값 사용
        configuration = FeignAuthConfig.class
)
public interface UserServerClient {
    @GetMapping("/api/users/me")
    MeResponse me();

    record MeResponse(Long userId, Long coupleId) {}
}
