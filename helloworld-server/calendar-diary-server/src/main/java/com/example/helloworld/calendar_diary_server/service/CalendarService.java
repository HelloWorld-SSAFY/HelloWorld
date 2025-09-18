package com.example.helloworld.calendar_diary_server.service;


import com.example.helloworld.calendar_diary_server.dto.CalendarEventDto;
import com.example.helloworld.calendar_diary_server.dto.CalendarEventsPageResponse;
import com.example.helloworld.calendar_diary_server.dto.CalendarRequestDto;
import com.example.helloworld.calendar_diary_server.dto.CalendarResponseDto;
import com.example.helloworld.calendar_diary_server.entity.CalendarEvent;
import com.example.helloworld.calendar_diary_server.repository.CalendarEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CalendarService {
    private final CalendarEventRepository calendarEventRepository;



    // 일정 생성
    public CalendarEventDto createEvent(Long coupleId, Long writerId, CalendarRequestDto req) {
        CalendarEvent entity = CalendarEvent.builder()
                .coupleId(coupleId)
                .writerId(writerId)
                .title(req.getTitle())
                .startAt(Instant.parse(req.getStartAt()))
                .endAt(req.getEndAt() != null ? Instant.parse(req.getEndAt()) : null)
                .memo(req.getMemo())
                .orderNo(req.getOrderNo())
                .isRemind(req.getIsRemind() != null && req.getIsRemind())
                .build();

        CalendarEvent saved = calendarEventRepository.save(entity);
        return CalendarEventDto.from(saved);
    }

    // 일정 수정
    public CalendarEventDto updateEvent(Long eventId, CalendarRequestDto req) {
        CalendarEvent entity = calendarEventRepository.findById(eventId)
                .orElseThrow(() -> new NoSuchElementException("404 일정 없음"));

        entity.setTitle(req.getTitle());
        entity.setStartAt(Instant.parse(req.getStartAt()));
        entity.setEndAt(req.getEndAt() != null ? Instant.parse(req.getEndAt()) : null);
        entity.setMemo(req.getMemo());
        entity.setOrderNo(req.getOrderNo());
        entity.setRemind(req.getIsRemind() != null && req.getIsRemind());

        CalendarEvent updated = calendarEventRepository.save(entity);
        return CalendarEventDto.from(updated);
    }

    // 일정 삭제
    public void deleteEvent(Long eventId) {
        if (!calendarEventRepository.existsById(eventId)) {
            throw new NoSuchElementException("404 일정 없음");
        }
        calendarEventRepository.deleteById(eventId);
    }

    // 일정 상세 조회
    public CalendarEventDto getEvent(Long eventId) {
        CalendarEvent entity = calendarEventRepository.findById(eventId)
                .orElseThrow(() -> new NoSuchElementException("404 일정 없음"));
        return CalendarEventDto.from(entity);
    }

    /**
     * 커플/기간/페이지 조건으로 조회.
     * - pageable == null 이면 전체 조회(Pageable.unpaged()).
     * - from, to, coupleId가 null이면 해당 조건은 무시(레포지토리 JPQL이 처리).
     */
    @Transactional(readOnly = true)
    public Page<CalendarEventDto> getAllEvents(Long coupleId, Instant from, Instant to, Pageable pageable) {

        Instant normalizedFrom = (from != null) ? from : Instant.EPOCH; // 1970-01-01T00:00:00Z
        Instant normalizedTo   = (to   != null) ? to   : Instant.parse("9999-12-31T23:59:59Z");

        Pageable page = (pageable != null)
                ? pageable
                : PageRequest.of(0, 200, Sort.by("startAt").ascending()); // page/size 안 주면 기본

        return calendarEventRepository
                .searchWithRange(coupleId, normalizedFrom, normalizedTo, page)
                .map(CalendarEventDto::from);
    }

    /**
     * 전체 조회 전용 헬퍼 (정렬 포함 원하면 컨트롤러에서 Pageable.ofSize(...) + Sort 전달)
     */
    @Transactional(readOnly = true)
    public Page<CalendarEventDto> getAll(Pageable pageable) {
        Pageable effectivePageable = (pageable == null) ? Pageable.unpaged() : pageable;
        return calendarEventRepository.findAll(effectivePageable)
                .map(this::toDto);
    }


    private CalendarEventDto toDto(CalendarEvent e) {
        return CalendarEventDto.builder()
                .eventId(e.getEventId())
                .title(e.getTitle())
                .memo(e.getMemo())
                .startAt(e.getStartAt())
                .endAt(e.getEndAt())
                .orderNo(e.getOrderNo())
                .isRemind(e.isRemind())
                .build();
    }
}
