package com.example.helloworld.healthserver.client;

import com.example.helloworld.healthserver.config.FeignAuthConfig;
import com.example.helloworld.healthserver.config.UserServerFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
@FeignClient(
        name = "user-server",
        url = "${userserver.base-url}",
        configuration = UserServerFeignConfig.class
)
public interface UserServerClient {

    @GetMapping("/api/internal/fcm/couples/{userId}/partner-latest")
    ResponseEntity<PartnerFcmResponse> partnerLatest(@PathVariable("userId") Long userId);

    @GetMapping("/api/internal/fcm/users/{userId}/latest")
    ResponseEntity<FcmTokenResponse> latestOfUser(@PathVariable("userId") Long userId);

    @GetMapping("/api/internal/fcm/couples/{userId}/both-latest")
    ResponseEntity<CoupleTokensResponse> bothLatest(@PathVariable("userId") Long userId);

    // DTO records
    record FcmTokenResponse(Long userId, String token) {}
    record PartnerFcmResponse(Long partnerId, String token) {}
    record CoupleTokensResponse(Long userId, String userToken, Long partnerId, String partnerToken) {}
}