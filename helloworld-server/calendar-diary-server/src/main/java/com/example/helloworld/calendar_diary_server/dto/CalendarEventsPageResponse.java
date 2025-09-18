package com.example.helloworld.calendar_diary_server.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarEventsPageResponse {
    private List<CalendarEventDto> events;

    private int page;
    private int size;

    @JsonProperty("total_elements")
    private long totalElements;
}
