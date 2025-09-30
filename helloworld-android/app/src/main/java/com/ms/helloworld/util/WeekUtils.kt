package com.ms.helloworld.util

import android.annotation.SuppressLint
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object WeekUtils {
    @SuppressLint("NewApi")
    fun getThisWeekRange(): Pair<String, String> {
        val today = LocalDate.now()

        // 일요일을 주의 시작으로 하는 경우
        val dayOfWeek = today.dayOfWeek.value % 7 // 월요일=1 -> 1, 일요일=0 -> 0
        val startOfWeek = today.minusDays(dayOfWeek.toLong()) // 이번 주 일요일
        val endOfWeek = startOfWeek.plusDays(6) // 이번 주 토요일

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return Pair(
            startOfWeek.format(formatter),
            endOfWeek.format(formatter)
        )
    }
}