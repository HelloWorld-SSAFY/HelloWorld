package com.example.helloworld.calendar_diary_server.dto;


import com.example.helloworld.calendar_diary_server.entity.CalendarEvent;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(name = "CalendarEvent")
public class CalendarEventDto {
    private Long eventId;
    private Long coupleId;
    private Long writerId;
    private String title;
    private Instant startAt;
    private Instant endAt;
    private String memo;
    private Integer orderNo;
    private boolean isRemind;

    public static CalendarEventDto from(CalendarEvent e) {
        if (e == null) return null;
        return CalendarEventDto.builder()
                .eventId(e.getEventId())
                .coupleId(e.getCoupleId())
                .writerId(e.getWriterId())
                .title(e.getTitle())
                .startAt(e.getStartAt())
                .endAt(e.getEndAt())
                .memo(e.getMemo())
                .orderNo(e.getOrderNo())
                .isRemind(e.isRemind())
                .build();
    }
}
