package com.example.helloworld.calendar_diary_server.controller;


import com.example.helloworld.calendar_diary_server.config.security.UserPrincipal;
import com.example.helloworld.calendar_diary_server.dto.*;
import com.example.helloworld.calendar_diary_server.entity.Diary;
import com.example.helloworld.calendar_diary_server.repository.DiaryRepository;
import com.example.helloworld.calendar_diary_server.service.DiaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;



@Tag(name = "Diary", description = "일기 API")
@RestController
@RequestMapping("/diary")
@RequiredArgsConstructor
public class DiaryController {
    private final DiaryService diaryApiService;


    @Value("${app.zone:Asia/Seoul}")
    private String appZone; //


    @GetMapping("/week")
    @Operation(
            summary = "임신 주차별 다이어리 조회 (GET)",
            description = "임신 n주차를 기준으로 (7n-6)일차 ~ 7n일차에 해당하는 target_date 범위를 계산해 해당 커플의 다이어리를 반환합니다."
    )

    public ResponseEntity<WeekResult> getByWeek(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam @Min(1) @Max(40) int week,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lmpDate
    ) {
        Long coupleId = getCoupleIdFromPrincipal(userPrincipal);
        return ResponseEntity.ok(diaryApiService.getByWeek(coupleId, week, lmpDate));
    }

    @GetMapping("/day")
    @Operation(
            summary = "임신 일차별 다이어리 조회 (GET)",
            description = "임신 day일차(1..280)를 LMP 기준으로 특정 날짜로 변환해 해당 커플의 다이어리를 반환합니다. 응답에 해당 주차도 함께 포함됩니다."
    )
    public ResponseEntity<DayResult> getByDay(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam @Min(1) @Max(280) int day,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lmpDate
    ) {
        Long coupleId = getCoupleIdFromPrincipal(userPrincipal);
        return ResponseEntity.ok(diaryApiService.getByDay(coupleId, day, lmpDate));
    }


    /** 6.1 일기 전체 조회 //앱범용// */
    @Operation(summary = " 일기 전체 조회 //앨범용//",
            description = """
            커플의 일기 목록을 최신순으로 조회합니다.
            - 필수: coupleId
            - 결과: 대표 이미지(있으면 1장)만 포함
            """)
    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        Long coupleId = userPrincipal.getCoupleId();// 토큰 기반의 안전한 coupleId 사용

        Page<DiaryListItemDto> entries = diaryApiService.list(coupleId, PageRequest.of(page, size));
        return ResponseEntity.ok(Map.of("entries", entries.getContent()));
    }

    /** 6.2 일기 상세 조회 */
    @Operation(summary = " 일기 상세 조회")
    @GetMapping("/{diaryId}")
    public ResponseEntity<DiaryDetailDto> detail(@PathVariable Long diaryId) {
        return ResponseEntity.ok(diaryApiService.detail(diaryId));
    }

    /** 6.3 일기 작성 */
    @Operation(summary = "일기 작성",
           description = "새로운 일정을 생성합니다. 인증된 사용자의 토큰을 기반으로 coupleId와 writerId가 자동으로 설정됩니다."
            )
    @PostMapping
    //  Controller로부터 신뢰할 수 있는 정보를 받도록 시그니처 변경
    public ResponseEntity<Map<String, String>> create(
            @Valid @RequestBody CreateDiaryRequest req,
           @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        Long coupleId = getCoupleIdFromPrincipal(userPrincipal);
        // 서비스에 신뢰할 수 있는 인증 정보(coupleId, userId, role)를 함께 전달
        // 서비스에 신뢰할 수 있는 인증 정보(coupleId, userId, role)를 함께 전달
        Long newDiaryId = diaryApiService.create(req, coupleId, userPrincipal.getUserId(), userPrincipal.getAuthorRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("diaryId", String.valueOf(newDiaryId)));
    }

    /** 6.4 일기 수정(이미지까지 수정) */
    @Operation(summary = "일기 수정(이미지까지 수정)",
            description = """
            전체 수정(덮어쓰기)입니다.
            - null이면 안 되는 필드: entryDate, diaryTitle, diaryContent
            - imageUrl: null을 보내면 이미지 변경하지 않음 / 빈 문자열("")을 보내면 이미지 제거
            """)
    @PutMapping("/{diaryId}")
    public ResponseEntity<?> update(@PathVariable Long diaryId,
                                    @Validated @RequestBody UpdateDiaryRequest req) {
        diaryApiService.update(diaryId, req);
        return ResponseEntity.ok(Map.of("diary_id", "d_" + diaryId, "updated", true));
    }

    /** 6.5 일기 삭제 */
    @Operation(summary = "일기 삭제")
    @DeleteMapping("/{diaryId}")
    public ResponseEntity<Void> delete(@PathVariable Long diaryId) {
        diaryApiService.delete(diaryId);
        return ResponseEntity.noContent().build();
    }

    /** 6.6 일기 사진 전체 조회(주마등용) */
    @Operation(summary = "일기 사진 전체 조회(주마등용)")
    @GetMapping("/photos")
    public ResponseEntity<List<DiaryPhotoDto>> allPhotos(@AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        Long coupleId = getCoupleIdFromPrincipal(userPrincipal);
        return ResponseEntity.ok(diaryApiService.allPhotos(coupleId));
    }



        /* UserPrincipal에서 coupleId를 추출하고, null일 경우 예외를 던지는 헬퍼 메소드
     */
    private Long getCoupleIdFromPrincipal(UserPrincipal userPrincipal) {
        Long coupleId = userPrincipal.getCoupleId();
        if (coupleId == null) {
            // 커플이 아닌 사용자가 커플 기능에 접근 시 에러 발생
            throw new AccessDeniedException("커플 정보가 없어 해당 기능에 접근할 수 없습니다.");
        }
        return coupleId;
    }
}
