package com.example.helloworld.calendar_diary_server.dto;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarRequestDto {

        private String title;
        private String startAt;
        private String endAt;
        private Boolean isRemind;
        private String memo;
        private Integer orderNo;
}
