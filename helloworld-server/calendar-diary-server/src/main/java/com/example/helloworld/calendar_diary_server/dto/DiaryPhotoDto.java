package com.example.helloworld.calendar_diary_server.dto;


import lombok.*;



//6.6 주마등 API(일기사진전체조회)응답에 사용
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiaryPhotoDto {
    private Long diaryId;
    private String imageUrl;
    private boolean isUltrasound;
}
