package com.ms.helloworld.dto.request

data class MemberUpdateRequest(
    val nickname: String? = null,
    val age: Int? = null
)