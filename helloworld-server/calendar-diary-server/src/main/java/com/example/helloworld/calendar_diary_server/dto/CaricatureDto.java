// dto/CaricatureDto.java
package com.example.helloworld.calendar_diary_server.dto;

import lombok.Builder;

@Builder
public record CaricatureDto(
        Long id,
        Long diaryPhotoId,
        String imageUrl    // presigned GET URL
) {}
