package com.ms.helloworld.dto.response

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class MaternalHealthGetResponse(
    @SerializedName("record_date")
    val recordDate: String, // YYYY-MM-DD

    @SerializedName("weight")
    val weight: BigDecimal,

    @SerializedName("blood_pressure")
    val bloodPressure: String, // "120/80"

    @SerializedName("blood_sugar")
    val bloodSugar: Int
)

data class MaternalHealthUpdateResponse(
    @SerializedName("maternal_id")
    val maternalId: String, // "mh_%d"

    @SerializedName("updated")
    val updated: Boolean
)

data class MaternalHealthListResponse(
    @SerializedName("records")
    val records: List<MaternalHealthItem>
)

data class MaternalHealthItem(
    @SerializedName("maternal_id")
    val maternalId: Long,

    @SerializedName("record_date")
    val recordDate: String,

    @SerializedName("weight")
    val weight: BigDecimal,

    @SerializedName("blood_pressure")
    val bloodPressure: String,

    @SerializedName("blood_sugar")
    val bloodSugar: Int,

    @SerializedName("created_at")
    val createdAt: String // UTC timestamp as string
)