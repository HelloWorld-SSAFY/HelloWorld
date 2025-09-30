package com.ms.helloworld.dto.response

import com.google.gson.annotations.SerializedName

data class CoupleInviteCodeResponse(
    @SerializedName("id") val id: Long,
    @SerializedName("code") val code: String,
    @SerializedName("couple_id") val coupleId: Long,
    @SerializedName("issuer_user_id") val issuerUserId: Long,
    @SerializedName("status") val status: String, // "ISSUED", "USED", "REVOKED", "EXPIRED"
    @SerializedName("expires_at") val expiresAt: String,
    @SerializedName("used_by_user_id") val usedByUserId: Long?,
    @SerializedName("used_at") val usedAt: String?
)