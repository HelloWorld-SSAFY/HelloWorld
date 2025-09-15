package com.example.helloworld.calendar_diary_server.dto;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiaryListItemDto {
    private String diaryId;     // "1"
    private String createdAt;   // "yyyy-MM-dd"
    private String diaryTitle;
    private String imageUrl;    // 대표 이미지(없으면 null)
    private String authorId;
    private String authorRole;  // "father" | "mother"
}
