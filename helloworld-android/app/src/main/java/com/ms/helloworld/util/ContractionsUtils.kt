package com.ms.helloworld.util

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ContractionsUtils {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSS")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun formatDateTime(dateTimeString: String): String {
        return try {
            val dateTime = LocalDateTime.parse(dateTimeString, dateTimeFormatter)
            val displayFormatter = DateTimeFormatter.ofPattern("MM월 dd일 HH:mm")
            dateTime.format(displayFormatter)
        } catch (e: Exception) {
            dateTimeString
        }
    }

    fun formatDuration(durationSec: Int): String {
        val duration = Duration.ofSeconds(durationSec.toLong())
        val minutes = duration.toMinutes()
        val seconds = duration.seconds % 60
        return "${minutes}분 ${seconds}초"
    }

    fun getCurrentDateTimeString(): String {
        return LocalDateTime.now().format(dateTimeFormatter)
    }

    fun getTodayDateString(): String {
        return LocalDate.now().format(dateFormatter)
    }
}