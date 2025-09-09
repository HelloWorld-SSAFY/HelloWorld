// TestLoggingUtils.kt - 실시간 데이터 로깅만 하는 간소화된 버전
package com.ms.wearos.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

object TestLoggingUtils {

    private const val TAG = "HealthDataLogger"
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // 실시간 심박수 데이터 로그
    fun logHeartRateData(heartRate: Double) {
        val timestamp = dateFormat.format(Date())
        Log.d(TAG, "[$timestamp] 심박수: ${heartRate.toInt()} BPM")
    }

    // 실시간 활동량 데이터 로그
    fun logActivityData(steps: Int, calories: Double, distance: Double) {
        val timestamp = dateFormat.format(Date())
        Log.d(TAG, """
            [$timestamp] 활동량 업데이트:
            걸음수: $steps
            칼로리: ${String.format("%.1f", calories)} kcal
            거리: ${String.format("%.2f", distance / 1000)} km
        """.trimIndent())
    }

    // 측정 시작/중지 로그
    fun logMeasurementStart(type: String) {
        val timestamp = dateFormat.format(Date())
        Log.i(TAG, "[$timestamp] $type 측정 시작")
    }

    fun logMeasurementStop(type: String) {
        val timestamp = dateFormat.format(Date())
        Log.i(TAG, "[$timestamp] $type 측정 중지")
    }
}