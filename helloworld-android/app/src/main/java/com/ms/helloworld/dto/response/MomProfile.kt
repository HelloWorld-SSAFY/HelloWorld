package com.ms.helloworld.dto.response

import java.time.LocalDate
import kotlin.math.abs

data class MomProfile(
    val nickname: String,
    val pregnancyWeek: Int,
    val dueDate: LocalDate,
    val lastMenstruationDate: LocalDate? = null
) {
    val currentDay: Int
        get() = (pregnancyWeek - 1) * 7 + 1

    fun getProfileImageResource(): String {
        return when (pregnancyWeek) {
            in 1..10 -> "pregnant_week_1_4"
            in 11..20 -> "pregnant_week_29_32"
            in 21..30 -> "pregnant_week_33_36"
            in 31..40 -> "pregnant_week_37_40"
            else -> "pregnant_default"
        }
    }

    fun getDaysUntilDue(): Int {
        val today = LocalDate.now()
        return abs(dueDate.toEpochDay() - today.toEpochDay()).toInt()
    }
}