package com.ms.helloworld.dto.request

import com.google.gson.annotations.SerializedName

data class CoupleInviteRequest(
    @SerializedName("code") val code: String
)