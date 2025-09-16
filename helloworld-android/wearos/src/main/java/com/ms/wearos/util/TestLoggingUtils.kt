package com.ms.wearos.util

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

    // 측정 시작/중지 로그
    fun logMeasurementStart() {
        val timestamp = dateFormat.format(Date())
        Log.i(TAG, "[$timestamp] 심박수 측정 시작")
    }

    fun logMeasurementStop(s: String) {
        val timestamp = dateFormat.format(Date())
        Log.i(TAG, "[$timestamp] 심박수 측정 중지")
    }
}