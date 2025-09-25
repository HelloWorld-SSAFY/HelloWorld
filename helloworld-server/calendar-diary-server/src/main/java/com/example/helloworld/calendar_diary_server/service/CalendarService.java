package com.example.helloworld.calendar_diary_server.service;


import com.example.helloworld.calendar_diary_server.client.HealthServerClient;
import com.example.helloworld.calendar_diary_server.dto.*;
import com.example.helloworld.calendar_diary_server.entity.CalendarEvent;
import com.example.helloworld.calendar_diary_server.repository.CalendarEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class CalendarService {
    private final CalendarEventRepository calendarEventRepository;
    private final HealthServerClient healthServerClient; // Feign 클라이언트 주입


    // 일정 생성
    //알림 설정(isRemind=true)이 되어 있으면 health-server에 알림 예약을 요청
    @Transactional
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

        // [추가된 로직] 알림 예약 요청
        scheduleReminderIfNecessary(saved);

        return CalendarEventDto.from(saved);
    }

    /**
     * 일정 수정
     * 알림 설정(isRemind=true)이 되어 있으면 health-server에 알림 예약을 요청합니다.
     * (기존 알림이 있었다면 덮어쓰게 됩니다)
     */
    @Transactional
    public CalendarEventDto updateEvent(Long eventId, CalendarRequestDto req) {
        CalendarEvent entity = calendarEventRepository.findById(eventId)
                .orElseThrow(() -> new NoSuchElementException("404 일정 없음"));

        // [alarm등록] 업데이트 전의 상태를 저장
        boolean wasRemindEnabled = entity.isRemind();
        Instant oldStartAt = entity.getStartAt();


        entity.setTitle(req.getTitle());
        entity.setStartAt(Instant.parse(req.getStartAt()));
        entity.setEndAt(req.getEndAt() != null ? Instant.parse(req.getEndAt()) : null);
        entity.setMemo(req.getMemo());
        entity.setOrderNo(req.getOrderNo());
        boolean isNowRemindEnabled = req.getIsRemind() != null && req.getIsRemind();
        entity.setRemind(isNowRemindEnabled);

        // 알림 취소 조건 확인 및 호출
        // 조건: 이전에 알림이 켜져 있었고, 이후에 알림을 끄거나 OR 시작 시간이 변경된 경우
        if (wasRemindEnabled && (!isNowRemindEnabled || !oldStartAt.equals(entity.getStartAt()))) {
            cancelReminderIfNecessary(entity.getWriterId(), oldStartAt);
        }


        CalendarEvent updated = calendarEventRepository.save(entity);

        //알림예약요청
        scheduleReminderIfNecessary(updated);

        return CalendarEventDto.from(updated);
    }
    /**
     * 일정 정보(CalendarEvent)를 바탕으로 알림이 필요하면 health-server API를 호출합니다.
     * @param event 저장되거나 수정된 일정 엔티티
     */
    private void scheduleReminderIfNecessary(CalendarEvent event) {
        // 1. 알림 설정이 true이고, 알림을 보낼 시간(startAt)이 지정되어 있는지 확인
        if (event.isRemind() && event.getStartAt() != null) {
            // 2. health-server로 보낼 메시지(DTO) 생성
            CalendarEventMessage message = new CalendarEventMessage(
                    event.getWriterId(),      // 알림을 받을 사용자 ID
                    "일정 알림",             // 고정된 알림 제목
                    event.getTitle(),         // 알림 내용은 일정 제목으로
                    event.getStartAt()        // 알림 시간은 일정 시작 시간으로
            );

            try {
                // 3. Feign 클라이언트를 통해 동기 호출
                log.info("Requesting reminder schedule to health-server for event: {}", event.getEventId());
                healthServerClient.scheduleReminder(message);
                log.info("Successfully requested a reminder schedule for user {}", event.getWriterId());
            } catch (Exception e) {
                // 4. health-server 호출 실패 시, 에러 로그를 남기고 RuntimeException을 발생시켜 트랜잭션을 롤백
                log.error("Failed to request a reminder schedule to health-server for event {}. Rolling back transaction.", event.getEventId(), e);
                throw new RuntimeException("Failed to schedule reminder. Cause: " + e.getMessage(), e);
            }
        }
        // 참고: isRemind가 false로 변경되었을 때, 기존 알림을 '취소'하는 로직은 health-server에 별도 API를 구현해야 합니다.
        // 현재는 생성/수정 시 isRemind가 true일 때만 예약을 요청합니다.
    }

    /**
     * 이전에 예약된 알림을 취소하도록 health-server API를 호출
     */
    private void cancelReminderIfNecessary(Long userId, Instant notifyAt) {
        if (userId == null || notifyAt == null) {
            return; // 취소할 정보가 없으면 즉시 리턴
        }

        CancelReminderRequest request = new CancelReminderRequest(userId, notifyAt);

        try {
            log.info("Requesting reminder cancellation to health-server for user {} at {}", userId, notifyAt);
            healthServerClient.cancelReminder(request);
            log.info("Successfully requested reminder cancellation.");
        } catch (Exception e) {
            // 중요: 알림 취소 실패는 일정 수정/삭제 자체를 롤백할 만큼 치명적이지 않습니다.
            // (이미 실행되었거나, 애초에 예약되지 않았을 수 있음)
            // 따라서 에러 로그만 남기고, 메인 트랜잭션은 계속 진행시킵니다.
            log.error("Failed to request a reminder cancellation to health-server. The main operation will continue. Details: {}", e.getMessage());
        }
    }




    // 일정 삭제
    @Transactional
    public void deleteEvent(Long eventId) {
        //  삭제 전에 엔티티를 먼저 조회
        CalendarEvent entity = calendarEventRepository.findById(eventId)
                .orElseThrow(() -> new NoSuchElementException("404 일정 없음"));

        //  알림이 켜져 있던 일정이었다면, 취소를 요청
        if (entity.isRemind()) {
            cancelReminderIfNecessary(entity.getWriterId(), entity.getStartAt());
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
