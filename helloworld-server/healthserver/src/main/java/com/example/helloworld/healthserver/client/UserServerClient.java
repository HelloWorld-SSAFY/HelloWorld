package com.example.helloworld.healthserver.client;

import com.example.helloworld.healthserver.config.FeignAuthConfig;
import com.example.helloworld.healthserver.config.UserServerFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
@FeignClient(name="user-server",
        url="${userserver.base-url}",
        configuration=UserServerFeignConfig.class)


public interface UserServerClient {
    @GetMapping("/api/internal/couples/{userId}/partner-id")
    ResponseEntity<PartnerIdResponse> partnerId(@PathVariable("userId") Long userId);

    @GetMapping("/api/internal/fcm/users/{userId}/latest-two")
    ResponseEntity<LatestTwoResponse> latestTwo(@PathVariable("userId") Long userId);

    @GetMapping("/api/internal/fcm/users/{userId}/latest")
    ResponseEntity<FcmTokenResponse> latestByPlatform(@PathVariable("userId") Long userId,
                                                      @RequestParam("platform") String platform);

    record PartnerIdResponse(Long partnerId) {}
    record LatestTwoResponse(Long userId, String androidToken, String watchToken) {}
    record FcmTokenResponse(Long userId, String platform, String token) {}
}