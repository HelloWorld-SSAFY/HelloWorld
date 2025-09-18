package com.example.helloworld.calendar_diary_server.dto;


import lombok.*;

import java.util.List;


//상세
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiaryDetailDto {
    private String createdAt;    // "yyyy-MM-dd"
    private String diaryTitle;
    private String diaryContent;
    private String imageUrl;     // 대표 이미지(없으면 null)

    private List<DiaryDetailPhotoDto> images;


    private String authorId;
    private String authorRole;   // "father" | "mother"
}
