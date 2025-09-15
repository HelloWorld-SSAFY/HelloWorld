package com.example.helloworld.calendar_diary_server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDiaryRequest {
    @NotBlank
    private String entryDate;   // "yyyy-MM-dd"
    @NotBlank private String diaryTitle;
    @NotBlank private String diaryContent;
    private String imageUrl;              // null 허용

    // 인증 컨텍스트에서 채운다고 해도, 여기선 명시
    @NotNull
    private Long coupleId;
    @NotNull private Long authorId;
    @NotBlank private String authorRole;  // "father" | "mother"
}
