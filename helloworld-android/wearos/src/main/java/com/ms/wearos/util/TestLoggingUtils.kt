package com.ms.wearos.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

object TestLoggingUtils {

    private const val TAG = "싸피_TestLogging"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * 심박수 데이터 로깅
     */
    fun logHeartRateData(heartRate: Double) {
        val timestamp = dateFormat.format(Date())
        val category = when {
            heartRate < 60 -> "서맥"
            heartRate > 100 -> "빈맥"
            else -> "정상"
        }

        Log.d(TAG, "=== 심박수 데이터 ===")
        Log.d(TAG, "시간: $timestamp")
        Log.d(TAG, "심박수: ${heartRate.toInt()} BPM")
        Log.d(TAG, "분류: $category")
        Log.d(TAG, "==================")
    }

    /**
     * 스트레스 데이터 로깅
     */
    fun logStressData(stressIndex: Int, stressLevel: String, advice: String) {
        val timestamp = dateFormat.format(Date())
        val stressColor = when (stressIndex) {
            in 0..20 -> "🟢"    // 녹색
            in 21..40 -> "🟡"   // 노란색
            in 41..60 -> "🟠"   // 주황색
            in 61..80 -> "🔴"   // 빨간색
            else -> "🚨"        // 경고
        }

        Log.d(TAG, "=== 스트레스 데이터 ===")
        Log.d(TAG, "시간: $timestamp")
        Log.d(TAG, "스트레스 지수: $stressIndex/100 $stressColor")
        Log.d(TAG, "스트레스 레벨: $stressLevel")
        Log.d(TAG, "조언: $advice")
        Log.d(TAG, "=====================")
    }

    /**
     * 통합 건강 데이터 로깅 (심박수 + 스트레스)
     */
    fun logHealthData(heartRate: Double, stressIndex: Int, stressLevel: String) {
        val timestamp = dateFormat.format(Date())
        val heartCategory = when {
            heartRate < 60 -> "서맥"
            heartRate > 100 -> "빈맥"
            else -> "정상"
        }

        val stressEmoji = when (stressIndex) {
            in 0..20 -> "😌"    // 매우 낮음
            in 21..40 -> "🙂"   // 낮음
            in 41..60 -> "😐"   // 보통
            in 61..80 -> "😰"   // 높음
            else -> "😱"        // 매우 높음
        }

        Log.d(TAG, "========================")
        Log.d(TAG, "   통합 건강 모니터링")
        Log.d(TAG, "========================")
        Log.d(TAG, "📅 시간: $timestamp")
        Log.d(TAG, "❤️ 심박수: ${heartRate.toInt()} BPM ($heartCategory)")
        Log.d(TAG, "🧠 스트레스: $stressIndex/100 ($stressLevel) $stressEmoji")

        // 위험 상황 감지
        if (heartRate > 120 || stressIndex >= 80) {
            Log.w(TAG, "⚠️ 주의: 비정상 수치 감지!")
            if (heartRate > 120) {
                Log.w(TAG, "   - 심박수가 매우 높습니다 (${heartRate.toInt()} BPM)")
            }
            if (stressIndex >= 80) {
                Log.w(TAG, "   - 스트레스 지수가 매우 높습니다 ($stressIndex/100)")
            }
        }

        Log.d(TAG, "========================")
    }

    /**
     * 측정 시작 로깅
     */
    fun logMeasurementStart() {
        val timestamp = dateFormat.format(Date())
        Log.i(TAG, "🚀 건강 모니터링 시작")
        Log.i(TAG, "시작 시간: $timestamp")
        Log.i(TAG, "측정 간격: 10초")
        Log.i(TAG, "측정 항목: 심박수, 스트레스 지수")
    }

    /**
     * 측정 종료 로깅
     */
    fun logMeasurementStop(measurementType: String) {
        val timestamp = dateFormat.format(Date())
        Log.i(TAG, "🛑 $measurementType 측정 종료")
        Log.i(TAG, "종료 시간: $timestamp")
    }

    /**
     * 에러 로깅
     */
    fun logError(errorType: String, errorMessage: String, exception: Exception? = null) {
        val timestamp = dateFormat.format(Date())
        Log.e(TAG, "❌ 오류 발생")
        Log.e(TAG, "시간: $timestamp")
        Log.e(TAG, "오류 유형: $errorType")
        Log.e(TAG, "오류 메시지: $errorMessage")
        exception?.let {
            Log.e(TAG, "예외 상세: ${it.localizedMessage}")
        }
    }

    /**
     * 센서 상태 로깅
     */
    fun logSensorStatus(sensorType: String, status: String, isAvailable: Boolean) {
        val timestamp = dateFormat.format(Date())
        val statusEmoji = if (isAvailable) "✅" else "❌"

        Log.i(TAG, "$statusEmoji 센서 상태 업데이트")
        Log.i(TAG, "시간: $timestamp")
        Log.i(TAG, "센서: $sensorType")
        Log.i(TAG, "상태: $status")
        Log.i(TAG, "사용 가능: $isAvailable")
    }

    /**
     * 성능 측정 로깅
     */
    fun logPerformance(operation: String, startTime: Long, endTime: Long) {
        val duration = endTime - startTime
        val timestamp = dateFormat.format(Date())

        Log.d(TAG, "⏱️ 성능 측정")
        Log.d(TAG, "시간: $timestamp")
        Log.d(TAG, "작업: $operation")
        Log.d(TAG, "소요 시간: ${duration}ms")

        if (duration > 1000) {
            Log.w(TAG, "⚠️ 긴 처리 시간 감지: ${duration}ms")
        }
    }

    /**
     * 알고리즘 상세 로깅 (디버깅용)
     */
    fun logAlgorithmDetails(
        heartRateHistory: List<Double>,
        hrvScore: Int,
        trendScore: Int,
        variabilityScore: Int,
        finalStress: Int
    ) {
        val timestamp = dateFormat.format(Date())

        Log.d(TAG, "🔍 스트레스 알고리즘 상세")
        Log.d(TAG, "시간: $timestamp")
        Log.d(TAG, "심박수 히스토리: $heartRateHistory")
        Log.d(TAG, "HRV 점수: $hrvScore")
        Log.d(TAG, "트렌드 점수: $trendScore")
        Log.d(TAG, "변동성 점수: $variabilityScore")
        Log.d(TAG, "최종 스트레스: $finalStress")
    }

    /**
     * 일일 요약 로깅
     */
    fun logDailySummary(
        totalMeasurements: Int,
        avgHeartRate: Double,
        avgStressIndex: Int,
        maxHeartRate: Double,
        maxStressIndex: Int
    ) {
        val timestamp = dateFormat.format(Date())

        Log.i(TAG, "📊 일일 건강 요약")
        Log.i(TAG, "날짜: $timestamp")
        Log.i(TAG, "총 측정 횟수: $totalMeasurements")
        Log.i(TAG, "평균 심박수: ${avgHeartRate.toInt()} BPM")
        Log.i(TAG, "평균 스트레스: $avgStressIndex/100")
        Log.i(TAG, "최고 심박수: ${maxHeartRate.toInt()} BPM")
        Log.i(TAG, "최고 스트레스: $maxStressIndex/100")
    }
}
