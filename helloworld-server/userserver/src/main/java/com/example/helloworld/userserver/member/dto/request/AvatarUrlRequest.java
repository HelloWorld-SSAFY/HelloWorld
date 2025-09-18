package com.example.helloworld.userserver.member.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name="AvatarUrlRequest")
public record AvatarUrlRequest(
        @JsonProperty("image_url")
        @Schema(description="프로필 이미지의 절대 URL (빈 값이면 해제)", example="https://cdn.example.com/u/17/avatar.jpg")
        String imageUrl
) {}
