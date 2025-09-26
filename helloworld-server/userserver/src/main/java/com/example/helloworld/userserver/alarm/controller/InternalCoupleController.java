package com.example.helloworld.userserver.alarm.controller;

import com.example.helloworld.userserver.member.persistence.CoupleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// package: com.example.helloworld.userserver.couple.controller (스캔되는 패키지면 OK)
@RestController
@RequestMapping("/api/internal/couples")
@RequiredArgsConstructor
public class InternalCoupleController {

    private final CoupleRepository coupleRepo;

    public record PartnerIdResponse(Long partnerId) {}

    @GetMapping("/{userId}/partner-id")
    public ResponseEntity<PartnerIdResponse> partnerId(@PathVariable Long userId) {
        return coupleRepo.findPartnerIdByUserId(userId)
                .map(id -> ResponseEntity.ok(new PartnerIdResponse(id)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}

