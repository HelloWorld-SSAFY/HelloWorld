package com.ms.wearos.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

object TestLoggingUtils {

    private const val TAG = "HealthDataLogger"
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    // 실시간 심박수 데이터 로그
    fun logHeartRateData(heartRate: Double) {
        val timestamp = dateFormat.format(Date())
        Log.d(TAG, "[$timestamp] 심박수: ${heartRate.toInt()} BPM")
    }

    // 측정 시작 로그
    fun logMeasurementStart() {
        val timestamp = fullDateFormat.format(Date())
        Log.i(TAG, "[$timestamp] ============================================")
        Log.i(TAG, "[$timestamp] 심박수 측정 시작 - 10초 간격으로 측정")
        Log.i(TAG, "[$timestamp] ============================================")
    }

    // 측정 중지 로그
    fun logMeasurementStop(measurementType: String) {
        val timestamp = fullDateFormat.format(Date())
        Log.i(TAG, "[$timestamp] ============================================")
        Log.i(TAG, "[$timestamp] $measurementType 측정 중지")
        Log.i(TAG, "[$timestamp] ============================================")
    }

    // 서비스 상태 변경 로그
    fun logServiceStatusChange(isRunning: Boolean, reason: String = "") {
        val timestamp = fullDateFormat.format(Date())
        val status = if (isRunning) "시작됨" else "중지됨"
        val reasonText = if (reason.isNotEmpty()) " - $reason" else ""

        Log.i(TAG, "[$timestamp] 서비스 상태 변경: $status$reasonText")
    }

    // 심박수 이상 징후 로그
    fun logHeartRateAnomaly(heartRate: Double, anomalyType: String) {
        val timestamp = fullDateFormat.format(Date())
        Log.w(TAG, "[$timestamp] 심박수 이상: $anomalyType - ${heartRate.toInt()} BPM")
    }

    // 백그라운드 서비스 상태 로그
    fun logBackgroundStatus(message: String) {
        val timestamp = dateFormat.format(Date())
        Log.d(TAG, "[$timestamp] [백그라운드] $message")
    }

    // UI 업데이트 로그
    fun logUIUpdate(heartRate: Double) {
        val timestamp = dateFormat.format(Date())
        Log.d(TAG, "[$timestamp] [UI 업데이트] 심박수: ${heartRate.toInt()} BPM")
    }

    // 권한 관련 로그
    fun logPermissionStatus(permission: String, granted: Boolean) {
        val timestamp = dateFormat.format(Date())
        val status = if (granted) "허용됨" else "거부됨"
        Log.i(TAG, "[$timestamp] 권한 $permission: $status")
    }

    // 센서 가용성 로그
    fun logSensorAvailability(availability: String) {
        val timestamp = dateFormat.format(Date())
        Log.d(TAG, "[$timestamp] 센서 상태: $availability")
    }

    // 토글 상태 변경 로그
    fun logToggleStateChange(enabled: Boolean, source: String = "") {
        val timestamp = fullDateFormat.format(Date())
        val state = if (enabled) "활성화" else "비활성화"
        val sourceText = if (source.isNotEmpty()) " ($source)" else ""

        Log.i(TAG, "[$timestamp] 토글 상태: $state$sourceText")
    }

    // 앱 생명주기 로그
    fun logAppLifecycle(event: String, details: String = "") {
        val timestamp = fullDateFormat.format(Date())
        val detailsText = if (details.isNotEmpty()) " - $details" else ""

        Log.i(TAG, "[$timestamp] [앱 생명주기] $event$detailsText")
    }

    // 에러 로그
    fun logError(error: String, exception: Throwable? = null) {
        val timestamp = fullDateFormat.format(Date())
        Log.e(TAG, "[$timestamp] 오류: $error", exception)
    }

    // 측정 세션 요약 로그
    fun logMeasurementSummary(
        sessionDuration: Long,
        measurementCount: Int,
        avgHeartRate: Double,
        minHeartRate: Double,
        maxHeartRate: Double
    ) {
        val timestamp = fullDateFormat.format(Date())
        val durationMinutes = sessionDuration / 60000

        Log.i(TAG, "[$timestamp] ============================================")
        Log.i(TAG, "[$timestamp] 측정 세션 요약:")
        Log.i(TAG, "[$timestamp] - 지속 시간: ${durationMinutes}분")
        Log.i(TAG, "[$timestamp] - 평균 심박수: ${avgHeartRate.toInt()} BPM")
        Log.i(TAG, "[$timestamp] - 최소 심박수: ${minHeartRate.toInt()} BPM")
        Log.i(TAG, "[$timestamp] - 최대 심박수: ${maxHeartRate.toInt()} BPM")
        Log.i(TAG, "[$timestamp] ============================================")
    }
}