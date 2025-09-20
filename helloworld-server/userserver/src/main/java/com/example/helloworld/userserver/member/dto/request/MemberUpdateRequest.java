package com.example.helloworld.userserver.member.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UserPartialUpdateRequest", description = "회원 부분 수정 요청(null은 유지)")
public record MemberUpdateRequest(
        String nickname,
        Integer age
) {}
