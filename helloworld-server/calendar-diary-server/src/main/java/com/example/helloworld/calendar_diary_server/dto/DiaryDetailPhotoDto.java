package com.example.helloworld.calendar_diary_server.dto;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiaryDetailPhotoDto {
    private String imageUrl;
    private boolean isUltrasound;
}
