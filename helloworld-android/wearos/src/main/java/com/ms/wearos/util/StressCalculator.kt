package com.ms.wearos.util

import android.util.Log
import kotlin.math.*

private const val TAG = "StressCalculator"

/**
 * 심박수 기반 스트레스 지수 계산기
 * HRV(Heart Rate Variability) 시뮬레이션과 심박수 패턴 분석을 통한 스트레스 측정
 */
object StressCalculator {

    // 최근 심박수 데이터를 저장할 큐 (최대 10개)
    private val heartRateHistory = mutableListOf<Double>()
    private val timestampHistory = mutableListOf<Long>()

    // 사용자 기본 정보 (실제로는 설정에서 가져와야 함)
    private var userAge = 30
    private var userRestingHeartRate = 65.0

    /**
     * 스트레스 지수 계산 (0-100)
     * @param currentHeartRate 현재 심박수
     * @return 스트레스 지수 (0: 매우 낮음, 100: 매우 높음)
     */
    fun calculateStressIndex(currentHeartRate: Double): Int {
        updateHeartRateHistory(currentHeartRate)

        val stressScore = when {
            heartRateHistory.size < 3 -> {
                // 초기 데이터 부족 시 기본 계산
                calculateBasicStress(currentHeartRate)
            }
            else -> {
                // 충분한 데이터가 있을 때 고급 계산
                calculateAdvancedStress(currentHeartRate)
            }
        }

        val finalStress = stressScore.coerceIn(0, 100)

        Log.d(TAG, "스트레스 지수 계산 완료: $finalStress")
        return finalStress
    }

    /**
     * 심박수 히스토리 업데이트
     */
    private fun updateHeartRateHistory(heartRate: Double) {
        val currentTime = System.currentTimeMillis()

        heartRateHistory.add(heartRate)
        timestampHistory.add(currentTime)

        // 최대 10개까지만 저장 (약 100초 분량)
        if (heartRateHistory.size > 10) {
            heartRateHistory.removeAt(0)
            timestampHistory.removeAt(0)
        }

        Log.d(TAG, "심박수 히스토리 업데이트: ${heartRateHistory.size}개 데이터")
    }

    /**
     * 기본 스트레스 계산 (데이터 부족 시)
     */
    private fun calculateBasicStress(heartRate: Double): Int {
        val maxHeartRate = 220 - userAge
        val heartRateReserve = maxHeartRate - userRestingHeartRate
        val currentIntensity = (heartRate - userRestingHeartRate) / heartRateReserve

        val baseStress = when {
            heartRate < userRestingHeartRate * 0.9 -> 15 // 너무 낮음
            heartRate < userRestingHeartRate * 1.1 -> 25 // 정상
            heartRate < userRestingHeartRate * 1.3 -> 45 // 약간 높음
            heartRate < userRestingHeartRate * 1.5 -> 65 // 높음
            else -> 85 // 매우 높음
        }

        Log.d(TAG, "기본 스트레스 계산: HR=$heartRate, 기준=${userRestingHeartRate}, 스트레스=$baseStress")
        return baseStress
    }

    /**
     * 고급 스트레스 계산 (충분한 데이터가 있을 때)
     */
    private fun calculateAdvancedStress(currentHeartRate: Double): Int {
        val hrvScore = calculateHRVSimulation()
        val trendScore = calculateHeartRateTrend()
        val variabilityScore = calculateHeartRateVariability()
        val restingDeviationScore = calculateRestingDeviation(currentHeartRate)

        // 가중 평균으로 최종 스트레스 점수 계산
        val finalScore = (
                hrvScore * 0.3 +           // HRV 시뮬레이션 30%
                        trendScore * 0.25 +        // 심박수 트렌드 25%
                        variabilityScore * 0.25 +  // 심박수 변동성 25%
                        restingDeviationScore * 0.2 // 안정시 심박수 편차 20%
                ).toInt()

        Log.d(TAG, "고급 스트레스 계산: HRV=$hrvScore, 트렌드=$trendScore, 변동성=$variabilityScore, 편차=$restingDeviationScore, 최종=$finalScore")
        return finalScore
    }

    /**
     * HRV(심박 변동성) 시뮬레이션
     * 실제 HRV는 R-R 간격 측정이 필요하지만, 심박수 패턴으로 추정
     */
    private fun calculateHRVSimulation(): Int {
        if (heartRateHistory.size < 3) return 50

        val intervals = mutableListOf<Double>()

        // 연속된 심박수 간의 차이 계산 (R-R 간격 변화량 시뮬레이션)
        for (i in 1 until heartRateHistory.size) {
            val interval = abs(heartRateHistory[i] - heartRateHistory[i-1])
            intervals.add(interval)
        }

        val avgInterval = intervals.average()
        val variance = intervals.map { (it - avgInterval).pow(2) }.average()
        val rmssd = sqrt(variance) // Root Mean Square of Successive Differences

        // RMSSD를 스트레스 점수로 변환 (낮은 변동성 = 높은 스트레스)
        val stressFromHRV = when {
            rmssd > 8.0 -> 20  // 높은 변동성 = 낮은 스트레스
            rmssd > 5.0 -> 35
            rmssd > 3.0 -> 50
            rmssd > 1.5 -> 70
            else -> 90         // 낮은 변동성 = 높은 스트레스
        }

        Log.d(TAG, "HRV 시뮬레이션: RMSSD=$rmssd, 스트레스=$stressFromHRV")
        return stressFromHRV
    }

    /**
     * 심박수 트렌드 분석 (상승/하강 패턴)
     */
    private fun calculateHeartRateTrend(): Int {
        if (heartRateHistory.size < 3) return 50

        val recentData = heartRateHistory.takeLast(5)
        var trendScore = 0

        // 연속적인 상승/하강 패턴 감지
        for (i in 1 until recentData.size) {
            val change = recentData[i] - recentData[i-1]
            when {
                change > 5 -> trendScore += 15   // 급격한 상승
                change > 2 -> trendScore += 8    // 완만한 상승
                change < -5 -> trendScore += 10  // 급격한 하강 (회복)
                change < -2 -> trendScore += 5   // 완만한 하강
                else -> trendScore += 3          // 안정적
            }
        }

        val finalTrendScore = (trendScore * 2).coerceIn(0, 100)
        Log.d(TAG, "트렌드 분석: 점수=$finalTrendScore")
        return finalTrendScore
    }

    /**
     * 심박수 변동성 분석
     */
    private fun calculateHeartRateVariability(): Int {
        if (heartRateHistory.size < 3) return 50

        val mean = heartRateHistory.average()
        val variance = heartRateHistory.map { (it - mean).pow(2) }.average()
        val standardDeviation = sqrt(variance)

        // 변동성이 클수록 스트레스가 높다고 가정
        val variabilityStress = when {
            standardDeviation > 15 -> 80  // 매우 높은 변동성
            standardDeviation > 10 -> 65  // 높은 변동성
            standardDeviation > 7 -> 50   // 보통 변동성
            standardDeviation > 4 -> 35   // 낮은 변동성
            else -> 25                    // 매우 낮은 변동성
        }

        Log.d(TAG, "변동성 분석: SD=$standardDeviation, 스트레스=$variabilityStress")
        return variabilityStress
    }

    /**
     * 안정시 심박수와의 편차 계산
     */
    private fun calculateRestingDeviation(currentHeartRate: Double): Int {
        val deviation = abs(currentHeartRate - userRestingHeartRate)
        val deviationPercentage = (deviation / userRestingHeartRate) * 100

        val deviationStress = when {
            deviationPercentage > 50 -> 90  // 50% 이상 편차
            deviationPercentage > 35 -> 75  // 35% 이상 편차
            deviationPercentage > 25 -> 60  // 25% 이상 편차
            deviationPercentage > 15 -> 45  // 15% 이상 편차
            deviationPercentage > 10 -> 30  // 10% 이상 편차
            else -> 20                      // 10% 미만 편차
        }

        Log.d(TAG, "편차 분석: 편차=${deviation.toInt()}BPM(${deviationPercentage.toInt()}%), 스트레스=$deviationStress")
        return deviationStress
    }

    /**
     * 스트레스 레벨을 텍스트로 변환
     */
    fun getStressLevelText(stressIndex: Int): String {
        return when (stressIndex) {
            in 0..20 -> "매우 낮음"
            in 21..40 -> "낮음"
            in 41..60 -> "보통"
            in 61..80 -> "높음"
            else -> "매우 높음"
        }
    }

    /**
     * 스트레스 지수에 따른 조언 메시지
     */
    fun getStressAdvice(stressIndex: Int): String {
        return when (stressIndex) {
            in 0..20 -> "매우 안정적인 상태입니다."
            in 21..40 -> "양호한 상태입니다."
            in 41..60 -> "적당한 스트레스 상태입니다."
            in 61..80 -> "스트레스가 높습니다. 휴식을 취하세요."
            else -> "매우 높은 스트레스! 즉시 휴식이 필요합니다."
        }
    }

    /**
     * 사용자 기본 정보 설정
     */
    fun setUserInfo(age: Int, restingHeartRate: Double) {
        userAge = age
        userRestingHeartRate = restingHeartRate
        Log.d(TAG, "사용자 정보 설정: 나이=$age, 안정시심박수=$restingHeartRate")
    }

    /**
     * 히스토리 초기화
     */
    fun clearHistory() {
        heartRateHistory.clear()
        timestampHistory.clear()
        Log.d(TAG, "히스토리 초기화됨")
    }
}