package com.example.helloworld.calendar_diary_server.controller;


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
            description = "커플 ID와 작성자 ID를 쿼리스트링으로 전달하고, 본문에는 일정 정보를 담아 새 일정을 생성합니다."
    )
    @PostMapping
    public ResponseEntity<Map<String, String>> createEvent(
            @RequestParam Long coupleId,
            @RequestParam Long writerId,
            @RequestBody CalendarRequestDto request) {

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
                    - 쿼리 파라미터를 **안 주면 전체 조회**(unpaged).
                    - coupleId, from, to를 주면 조건 조회.
                    - page, size를 주면 페이징.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "OK",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = CalendarEventDto.class))))
            }
    )
    @GetMapping("/events")
    public Page<CalendarEventDto> getEvents(
            @RequestParam(required = false) Long coupleId,
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

        return calendarService.getAllEvents(coupleId, from, to, pageable);
    }
}
