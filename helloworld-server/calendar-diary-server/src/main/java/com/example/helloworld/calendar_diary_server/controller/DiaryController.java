package com.example.helloworld.calendar_diary_server.controller;


import com.example.helloworld.calendar_diary_server.dto.*;
import com.example.helloworld.calendar_diary_server.service.DiaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "Diary", description = "일기 API")
@RestController
@RequestMapping("/diary")
@RequiredArgsConstructor
public class DiaryController {
    private final DiaryService diaryApiService;



    @GetMapping("/week")
    @Operation(
            summary = "임신 주차별 다이어리 조회 (GET)",
            description = "임신 n주차를 기준으로 (7n-6)일차 ~ 7n일차에 해당하는 target_date 범위를 계산해 해당 커플의 다이어리를 반환합니다."
    )

    public ResponseEntity<WeekResult> getByWeek(
            @Parameter(description = "커플 ID", example = "1")
            @RequestParam Long coupleId,

            @Parameter(description = "임신 주차(1..40)", example = "2")
            @RequestParam @Min(1) @Max(40) int week,

            @Parameter(description = "LMP(마지막 생리일). ISO 날짜(YYYY-MM-DD)",
                    schema = @Schema(type = "string", format = "date", example = "2025-01-10"))
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lmpDate
    ) {
        return ResponseEntity.ok(diaryApiService.getByWeek(coupleId, week, lmpDate));
    }

    @GetMapping("/day")
    @Operation(
            summary = "임신 일차별 다이어리 조회 (GET)",
            description = "임신 day일차(1..280)를 LMP 기준으로 특정 날짜로 변환해 해당 커플의 다이어리를 반환합니다. 응답에 해당 주차도 함께 포함됩니다."
    )
    public ResponseEntity<DayResult> getByDay(
            @Parameter(description = "커플 ID", example = "1")
            @RequestParam Long coupleId,

            @Parameter(description = "임신 일차(1..280)", example = "4")
            @RequestParam @Min(1) @Max(280) int day,

            @Parameter(description = "LMP(마지막 생리일). ISO 날짜(YYYY-MM-DD)",
                    schema = @Schema(type = "string", format = "date", example = "2025-01-10"))
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lmpDate
    ) {
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
    public ResponseEntity<?> list(@RequestParam Long coupleId,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
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
            description = """
            일기를 새로 작성합니다.
            - null이면 안 되는 필드: entryDate, diaryTitle, diaryContent, coupleId, authorId, authorRole
            - null 허용: imageUrl
            """)
    @PostMapping
    public ResponseEntity<?> create(@Validated @RequestBody CreateDiaryRequest req) {
        Long id = diaryApiService.create(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("diary_id", String.valueOf(id)));
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
    public ResponseEntity<List<DiaryPhotoDto>> allPhotos(@RequestParam Long coupleId) {
        return ResponseEntity.ok(diaryApiService.allPhotos(coupleId));
    }


}
