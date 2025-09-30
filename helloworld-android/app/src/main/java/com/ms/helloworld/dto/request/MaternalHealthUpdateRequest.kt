package com.ms.helloworld.dto.request

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class MaternalHealthUpdateRequest(
    @SerializedName("weight")
    val weight: BigDecimal? = null,

    @SerializedName("blood_pressure")
    val bloodPressure: String? = null, // "120/80" format

    @SerializedName("blood_sugar")
    val bloodSugar: Int? = null,

    @SerializedName("updated_at")
    val updatedAt: String
)