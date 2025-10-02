package com.example.helloworld.userserver.member.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

@Schema(name = "UserRegisterRequest", description = "회원+커플 공유 정보 통합 입력 요청")
public record MemberRegisterRequest(
        @NotBlank @Size(min = 2, max = 20)
        String nickname,

        @NotBlank @Pattern(regexp = "^(female|male)$")
        String gender,

        @NotNull @Min(0) @Max(120)
        Integer age
) {}

