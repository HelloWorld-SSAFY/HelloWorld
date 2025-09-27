package com.example.helloworld.calendar_diary_server.service;

import com.example.helloworld.calendar_diary_server.client.HealthServerClient;
import com.example.helloworld.calendar_diary_server.dto.*;
import com.example.helloworld.calendar_diary_server.entity.CalendarEvent;
import com.example.helloworld.calendar_diary_server.repository.CalendarEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Slf4j
public class CalendarService {
    private final CalendarEventRepository calendarEventRepository;
    private final HealthServerClient healthServerClient;

    // 필요 시 내부 고정 토큰(게이트웨이 우회 시 유용). 없다면 빈 값으로 두어도 무방.
    @Value("${internal.app-token:}")
    private String appToken;

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
                .isRemind(Boolean.TRUE.equals(req.getIsRemind()))
                .build();

        CalendarEvent saved = calendarEventRepository.save(entity);

        // 커밋 후 알림 예약
        scheduleReminderAfterCommit(saved);

        return CalendarEventDto.from(saved);
    }

    @Transactional
    public CalendarEventDto updateEvent(Long eventId, CalendarRequestDto req) {
        CalendarEvent entity = calendarEventRepository.findById(eventId)
                .orElseThrow(() -> new NoSuchElementException("404 일정 없음"));

        boolean wasRemindEnabled = entity.isRemind();
        Instant oldStartAt = entity.getStartAt();

        entity.setTitle(req.getTitle());
        entity.setStartAt(Instant.parse(req.getStartAt()));
        entity.setEndAt(req.getEndAt() != null ? Instant.parse(req.getEndAt()) : null);
        entity.setMemo(req.getMemo());
        entity.setOrderNo(req.getOrderNo());
        boolean isNowRemindEnabled = Boolean.TRUE.equals(req.getIsRemind());
        entity.setRemind(isNowRemindEnabled);

        // 알림 취소는 커밋 후로 밀어 순수 DB 갱신을 보호
        if (wasRemindEnabled && (!isNowRemindEnabled || !oldStartAt.equals(entity.getStartAt()))) {
            cancelReminderAfterCommit(entity.getWriterId(), entity.getCoupleId(), oldStartAt);
        }

        CalendarEvent updated = calendarEventRepository.save(entity);

        // 재예약(켜짐 상태면)
        scheduleReminderAfterCommit(updated);

        return CalendarEventDto.from(updated);
    }

    @Transactional
    public void deleteEvent(Long eventId) {
        CalendarEvent entity = calendarEventRepository.findById(eventId)
                .orElseThrow(() -> new NoSuchElementException("404 일정 없음"));

        if (entity.isRemind()) {
            cancelReminderAfterCommit(entity.getWriterId(), entity.getCoupleId(), entity.getStartAt());
        }

        calendarEventRepository.deleteById(eventId);
    }

    // === 커밋 후 외부 호출 래퍼들 ===

    private void scheduleReminderAfterCommit(CalendarEvent event) {
        if (!event.isRemind() || event.getStartAt() == null) return;

        CalendarEventMessage message = new CalendarEventMessage(
                event.getWriterId(),
                "일정 알림",
                event.getTitle(),
                event.getStartAt()
        );

        Long userId = event.getWriterId();
        Long coupleId = event.getCoupleId();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    log.info("AFTER_COMMIT: schedule reminder eventId={} userId={} coupleId={}",
                            event.getEventId(), userId, coupleId);
                    healthServerClient.scheduleReminder(userId, coupleId, appToken, message);
                } catch (Exception e) {
                    // 커밋 이후 실패는 롤백할 수 없으므로 로깅 + 추후 재시도 큐 권장
                    log.error("Failed to schedule reminder (post-commit). eventId={}", event.getEventId(), e);
                }
            }
        });
    }

    private void cancelReminderAfterCommit(Long userId, Long coupleId, Instant notifyAt) {
        if (userId == null || coupleId == null || notifyAt == null) return;

        CancelReminderRequest request = new CancelReminderRequest(userId, notifyAt);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    log.info("AFTER_COMMIT: cancel reminder userId={} coupleId={} at={}", userId, coupleId, notifyAt);
                    healthServerClient.cancelReminder(userId, coupleId, appToken, request);
                } catch (Exception e) {
                    log.error("Failed to cancel reminder (post-commit). userId={}, at={}", userId, notifyAt, e);
                }
            }
        });
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
