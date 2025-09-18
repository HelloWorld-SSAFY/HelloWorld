package com.example.helloworld.calendar_diary_server.dto;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarResponseDto {
    private String eventId; // e_0001 형식
    private String title;
    private String startAt;
    private String endAt;
    private Boolean isRemind;
    private String memo;
    private String createdAt;
    private Integer orderNo;
}
