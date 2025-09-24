package com.ms.helloworld.dto.response

import com.google.gson.annotations.SerializedName

data class WeeklyInfoResponse(
    val weekNo: Int,
    val info: String
)

data class WeeklyWorkoutsResponse(
    val weekNo: Int,
    val items: List<WorkoutItem>
)

data class WorkoutItem(
    val type: WorkoutType,
    val text: String?,
    val title: String?,
    val url: String?,
    val thumbnailUrl: String?,
    val orderNo: Int?
)

enum class WorkoutType {
    TEXT, VIDEO
}

data class WeeklyDietsResponse(
    val weekNo: Int,
    val fromDayInWeek: Int,
    val toDayInWeek: Int,
    val days: List<DietDay>
)

data class DietDay(
    val dayInWeek: Int,
    val dayNo: Int,
    val food: String,
    val detail: String,
    val imgUrl: String?
)