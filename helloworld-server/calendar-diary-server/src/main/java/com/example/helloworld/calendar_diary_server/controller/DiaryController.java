package com.example.helloworld.calendar_diary_server.controller;


import com.example.helloworld.calendar_diary_server.dto.*;
import com.example.helloworld.calendar_diary_server.service.DiaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Diary", description = "일기 API")
@RestController
@RequestMapping("/diary")
@RequiredArgsConstructor
public class DiaryController {
    private final DiaryService diaryApiService;

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
