// controller/CaricatureController.java
package com.example.helloworld.calendar_diary_server.controller;

import com.example.helloworld.calendar_diary_server.config.security.UserPrincipal;
import com.example.helloworld.calendar_diary_server.dto.CaricatureDto;
import com.example.helloworld.calendar_diary_server.service.CaricatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Caricature", description = "캐리커처 생성/조회 API")
@RestController
@RequestMapping("/caricatures")
@RequiredArgsConstructor
public class CaricatureController {

    private final CaricatureService caricatureService;

    @Operation(summary = "캐리커처 생성", description = "초음파 사진(diaryPhotoId)로 캐리커처를 생성하고 S3에 저장한 뒤 presigned URL을 돌려줍니다.")
    @PostMapping("/from-photo/{diaryPhotoId}")
    public ResponseEntity<CaricatureDto> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long diaryPhotoId
    ) throws Exception {
        Long coupleId = principal.getCoupleId();
        CaricatureDto dto = caricatureService.generateFromPhoto(coupleId, diaryPhotoId);
        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "최근 생성본 조회", description = "해당 사진의 가장 최근 캐리커처를 presigned URL로 반환합니다.")
    @GetMapping("/from-photo/{diaryPhotoId}")
    public ResponseEntity<CaricatureDto> getLatest(
            @PathVariable Long diaryPhotoId
    ) {
        var dto = caricatureService.getLatest(diaryPhotoId);
        return ResponseEntity.ok(dto);
    }
}
