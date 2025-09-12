package com.example.helloworld.calendar_diary_server.repository;

import com.example.helloworld.calendar_diary_server.entity.CalendarEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    @Query("""
        SELECT e FROM CalendarEvent e
        WHERE (:coupleId IS NULL OR e.coupleId = :coupleId)
          AND e.startAt >= :from
          AND e.startAt <= :to
        """)
    Page<CalendarEvent> searchWithRange(@Param("coupleId") Long coupleId,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to,
                                        Pageable pageable);
}