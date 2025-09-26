package com.example.helloworld.userserver.alarm.controller;

import com.example.helloworld.userserver.alarm.persistence.DeviceTokenRepository;
import com.example.helloworld.userserver.member.persistence.CoupleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// user-server
@RestController
@RequestMapping("/api/internal/fcm")
@RequiredArgsConstructor
public class InternalFcmController {

    private final DeviceTokenRepository tokenRepo;
    private final CoupleRepository coupleRepo; // 사용자→커플/파트너 조회용

    public record FcmTokenResponse(Long userId, String token) {}
    public record PartnerFcmResponse(Long partnerId, String token) {}
    public record CoupleTokensResponse(Long userId, String userToken,
                                       Long partnerId, String partnerToken) {}

    @GetMapping("/users/{userId}/latest")
    public ResponseEntity<FcmTokenResponse> latestOfUser(@PathVariable Long userId) {
        var opt = tokenRepo.findFirstByUserIdAndIsActiveTrueOrderByLastSeenAtDescCreatedAtDesc(userId);
        return opt.map(dt -> ResponseEntity.ok(new FcmTokenResponse(userId, dt.getToken())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/couples/{userId}/partner-latest")
    public ResponseEntity<PartnerFcmResponse> partnerLatest(@PathVariable Long userId) {
        var c = coupleRepo.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "couple not found"));

        Long aId = c.getUserA() != null ? c.getUserA().getId() : null;       // ManyToOne → id
        Long bId = c.getUserB() != null ? c.getUserB().getId() : null;       // userB는 null 가능

        Long partnerId = userId.equals(aId) ? bId
                : (bId != null && userId.equals(bId) ? aId : null);

        if (partnerId == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "user not in couple");

        var opt = tokenRepo.findFirstByUserIdAndIsActiveTrueOrderByLastSeenAtDescCreatedAtDesc(partnerId);
        return opt.map(dt -> ResponseEntity.ok(new PartnerFcmResponse(partnerId, dt.getToken())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/couples/{userId}/both-latest")
    public ResponseEntity<CoupleTokensResponse> bothLatest(@PathVariable Long userId) {
        var c = coupleRepo.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "couple not found"));

        Long aId = c.getUserA() != null ? c.getUserA().getId() : null;
        Long bId = c.getUserB() != null ? c.getUserB().getId() : null;

        Long partnerId = userId.equals(aId) ? bId
                : (bId != null && userId.equals(bId) ? aId : null);

        if (partnerId == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "user not in couple");

        var u = tokenRepo.findFirstByUserIdAndIsActiveTrueOrderByLastSeenAtDescCreatedAtDesc(userId).orElse(null);
        var p = tokenRepo.findFirstByUserIdAndIsActiveTrueOrderByLastSeenAtDescCreatedAtDesc(partnerId).orElse(null);

        if (u == null && p == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(new CoupleTokensResponse(
                userId,    u != null ? u.getToken() : null,
                partnerId, p != null ? p.getToken() : null
        ));
    }
}
