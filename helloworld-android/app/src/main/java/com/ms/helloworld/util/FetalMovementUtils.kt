package com.ms.helloworld.util

import com.ms.helloworld.dto.response.FetalMovementRecord

object FetalMovementUtils {
    fun calculateWeeklyAverage(records: List<FetalMovementRecord>): Double {
        return if (records.isEmpty()) {
            0.0
        } else {
            val totalCount = records.sumOf { it.totalCount }
            totalCount.toDouble() / records.size
        }
    }

    fun formatAverage(average: Double): String {
        return String.format("%.1f", average)
    }
}