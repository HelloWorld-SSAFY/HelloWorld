package com.example.helloworld.calendar_diary_server.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateDiaryRequest {
    @NotBlank
    private String entryDate;   // "yyyy-MM-dd"
    @NotBlank private String diaryTitle;
    @NotBlank private String diaryContent;
    private String imageUrl;              // null이면 이미지 변경 안 함
}
