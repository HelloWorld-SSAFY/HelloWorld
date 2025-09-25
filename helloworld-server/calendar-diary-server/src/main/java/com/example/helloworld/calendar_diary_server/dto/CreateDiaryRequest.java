package com.example.helloworld.calendar_diary_server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.cglib.core.Local;

import java.sql.Timestamp;
import java.time.LocalDate;


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
//    private String imageUrl;              // null 허용

    // 인증 컨텍스트에서 채운다고 해도, 여기선 명시

    //jwt헤더정보로 읽어옮.
//    @NotNull
//    private Long coupleId;
//    @NotNull private Long authorId;
//    @NotBlank private String authorRole;  // "father" | "mother"

    @NotNull private LocalDate targetDate;

}
