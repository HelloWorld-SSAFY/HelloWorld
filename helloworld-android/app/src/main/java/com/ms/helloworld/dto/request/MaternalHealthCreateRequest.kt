package com.ms.helloworld.dto.request

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class MaternalHealthCreateRequest(
    @SerializedName("weight")
    val weight: BigDecimal,

    @SerializedName("max_blood_pressure")
    val maxBloodPressure: Int,

    @SerializedName("min_blood_pressure")
    val minBloodPressure: Int,

    @SerializedName("blood_sugar")
    val bloodSugar: Int
)