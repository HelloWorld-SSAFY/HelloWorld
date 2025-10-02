package com.example.helloworld.calendar_diary_server.controller;


import com.example.helloworld.calendar_diary_server.config.security.UserPrincipal;
import com.example.helloworld.calendar_diary_server.dto.CalendarEventDto;
import com.example.helloworld.calendar_diary_server.dto.CalendarRequestDto;
import com.example.helloworld.calendar_diary_server.service.CalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/calendar")
@RequiredArgsConstructor
public class CalendarController {
    private final CalendarService calendarService;


    // 일정 생성
    @Operation(
            summary = "일정 생성",
            description =  "새로운 일정을 생성합니다. 인증된 사용자의 토큰을 기반으로 coupleId와 writerId가 자동으로 설정됩니다."
    )
    @PostMapping
    public ResponseEntity<Map<String, String>> createEvent(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody CalendarRequestDto request) {

        Long coupleId = getCoupleIdFromPrincipal(userPrincipal);
        Long writerId = userPrincipal.getUserId();

        CalendarEventDto dto = calendarService.createEvent(coupleId, writerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("event_id", "e_" + dto.getEventId()));
    }

    // 일정 수정
    @Operation(
            summary = "일정 수정",
            description = "이벤트 ID로 대상을 지정하여 제목/시간/메모/리마인드/순번 등을 수정합니다."
    )
    @PutMapping("/{eventId}")
    public ResponseEntity<Map<String, Object>> updateEvent(
            @PathVariable Long eventId,
            @RequestBody CalendarRequestDto request) {

        calendarService.updateEvent(eventId, request);
        return ResponseEntity.ok(Map.of("event_id", "e_" + eventId, "updated", true));
    }

    // 일정 삭제
    @Operation(
            summary = "일정 삭제",
            description = "이벤트 ID로 대상을 지정하여 일정을 삭제합니다."
    )
    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long eventId) {
        calendarService.deleteEvent(eventId);
        return ResponseEntity.noContent().build();
    }

    // 일정 상세 조회
    @Operation(
            summary = "일정 상세 조회",
            description = "이벤트 ID로 일정을 조회합니다."
    )
    @GetMapping("/{eventId}")
    public ResponseEntity<CalendarEventDto> getEvent(@PathVariable Long eventId) {
        return ResponseEntity.ok(calendarService.getEvent(eventId));
    }



    @Operation(
            summary = "일정 조회(전체/조건)",
            description = """
                    인증된 사용자의 커플 ID를 기준으로 일정 목록을 조회합니다.
                    - `from`, `to`: 특정 기간 내의 일정을 조회합니다. (ISO 8601 형식, 예: `2023-10-27T10:00:00Z`)
                    - `page`, `size`: 결과를 페이징하여 조회합니다. (예: `page=0&size=20`)
                    - `sort`: 결과를 정렬합니다. (예: `sort=startAt,asc`)
                    - 모든 쿼리 파라미터는 선택사항입니다.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "OK",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = CalendarEventDto.class))))
            }
    )
    @GetMapping("/events")
    public Page<CalendarEventDto> getEvents(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        Pageable pageable = (page == null && size == null)
                ? Pageable.unpaged()
                : org.springframework.data.domain.PageRequest.of(
                page == null ? 0 : page,
                size == null ? 20 : size,
                Sort.by(Sort.Order.asc("startAt"), Sort.Order.asc("eventId"))
        );
        Long coupleId = getCoupleIdFromPrincipal(userPrincipal);
        return calendarService.getAllEvents(coupleId, from, to, pageable);
}

    /**
     * UserPrincipal에서 coupleId를 추출하고, null일 경우 예외를 던지는 헬퍼 메소드
     */
    private Long getCoupleIdFromPrincipal(UserPrincipal userPrincipal) {
        Long coupleId = userPrincipal.getCoupleId();
        if (coupleId == null) {
            throw new AccessDeniedException("커플 정보가 없어 해당 기능에 접근할 수 없습니다.");
        }
        return coupleId;
    }
}
