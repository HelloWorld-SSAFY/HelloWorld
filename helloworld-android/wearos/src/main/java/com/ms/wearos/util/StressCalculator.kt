package com.ms.wearos.util

import android.util.Log
import kotlin.math.*

private const val TAG = "EnhancedStressCalculator"

/**
 * 향상된 스트레스 지수 계산기
 * 개선된 HRV 추정과 더 정교한 알고리즘 사용
 */
object EnhancedStressCalculator {

    // 최근 심박수 데이터를 저장할 큐 (최대 15개로 확장)
    private val heartRateHistory = mutableListOf<Double>()
    private val timestampHistory = mutableListOf<Long>()

    // 사용자 기본 정보
    private var userAge = 30
    private var userRestingHeartRate = 65.0

    /**
     * 향상된 스트레스 지수 계산 (0-100)
     */
    fun calculateStressIndex(currentHeartRate: Double): Int {
        updateHeartRateHistory(currentHeartRate)

        val stressScore = when {
            heartRateHistory.size < 3 -> {
                calculateBasicStress(currentHeartRate)
            }
            heartRateHistory.size < 6 -> {
                calculateIntermediateStress(currentHeartRate)
            }
            else -> {
                calculateAdvancedStressWithHRV(currentHeartRate)
            }
        }

        // 연령 조정 적용
        val ageAdjustedScore = ImprovedHRVCalculator.getAgeAdjustedHRVScore(userAge, stressScore)
        val finalStress = ageAdjustedScore.coerceIn(0, 100)

        Log.d(TAG, "향상된 스트레스 지수 계산 완료: $finalStress")
        return finalStress
    }

    /**
     * 심박수 히스토리 업데이트 (확장된 버퍼)
     */
    private fun updateHeartRateHistory(heartRate: Double) {
        val currentTime = System.currentTimeMillis()

        heartRateHistory.add(heartRate)
        timestampHistory.add(currentTime)

        // 최대 15개까지 저장 (약 150초 분량)
        if (heartRateHistory.size > 15) {
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
        val targetZone = userRestingHeartRate + (maxHeartRate - userRestingHeartRate) * 0.5

        val baseStress = when {
            heartRate < userRestingHeartRate * 0.85 -> 25  // 너무 낮음 (이상 상황)
            heartRate < userRestingHeartRate * 1.1 -> 30   // 정상 안정 상태
            heartRate < targetZone * 0.7 -> 45              // 약간 상승
            heartRate < targetZone -> 60                    // 중간 강도
            heartRate < targetZone * 1.3 -> 75              // 높음
            else -> 85                                      // 매우 높음
        }

        Log.d(TAG, "기본 스트레스 계산: HR=$heartRate, 목표구간=${targetZone.toInt()}, 스트레스=$baseStress")
        return baseStress
    }

    /**
     * 중간 단계 스트레스 계산 (3-5개 데이터)
     */
    private fun calculateIntermediateStress(currentHeartRate: Double): Int {
        val basicScore = calculateBasicStress(currentHeartRate)
        val simpleVariability = calculateSimpleVariability()
        val simpleTrend = calculateSimpleTrend()

        // 가중 평균
        val intermediateScore = (basicScore * 0.6 + simpleVariability * 0.25 + simpleTrend * 0.15).toInt()

        Log.d(TAG, "중간 스트레스 계산: 기본=$basicScore, 변동성=$simpleVariability, 트렌드=$simpleTrend, 최종=$intermediateScore")
        return intermediateScore
    }

    /**
     * 고급 스트레스 계산 (HRV 포함)
     */
    private fun calculateAdvancedStressWithHRV(currentHeartRate: Double): Int {
        // 향상된 HRV 계산
        val hrvResult = ImprovedHRVCalculator.calculateImprovedHRV(heartRateHistory)
        val hrvQuality = ImprovedHRVCalculator.assessHRVQuality(heartRateHistory)

        // 기존 지표들도 계산
        val trendScore = calculateHeartRateTrend()
        val variabilityScore = calculateHeartRateVariability()
        val restingDeviationScore = calculateRestingDeviation(currentHeartRate)
        val temporalScore = calculateTemporalPatterns()

        // HRV 품질에 따른 가중치 조정
        val hrvWeight = when (hrvQuality) {
            ImprovedHRVCalculator.HRVQuality.EXCELLENT -> 0.4
            ImprovedHRVCalculator.HRVQuality.GOOD -> 0.35
            ImprovedHRVCalculator.HRVQuality.FAIR -> 0.25
            ImprovedHRVCalculator.HRVQuality.POOR -> 0.15
        }

        val otherWeight = 1.0 - hrvWeight

        // 가중 평균으로 최종 스트레스 점수 계산
        val finalScore = (
                hrvResult.stressScore * hrvWeight +
                        trendScore * (otherWeight * 0.3) +
                        variabilityScore * (otherWeight * 0.25) +
                        restingDeviationScore * (otherWeight * 0.25) +
                        temporalScore * (otherWeight * 0.2)
                ).toInt()

        Log.d(TAG, "고급 스트레스 계산: HRV=${hrvResult.stressScore}(${hrvQuality}), " +
                "트렌드=$trendScore, 변동성=$variabilityScore, 편차=$restingDeviationScore, " +
                "시간패턴=$temporalScore, 최종=$finalScore")

        return finalScore
    }

    /**
     * 단순 변동성 계산 (데이터 부족 시)
     */
    private fun calculateSimpleVariability(): Int {
        if (heartRateHistory.size < 3) return 50

        val range = heartRateHistory.maxOrNull()!! - heartRateHistory.minOrNull()!!

        return when {
            range > 20 -> 75  // 높은 변동성
            range > 10 -> 60  // 중간 변동성
            range > 5 -> 45   // 낮은 변동성
            else -> 35        // 매우 낮은 변동성
        }
    }

    /**
     * 단순 트렌드 계산 (데이터 부족 시)
     */
    private fun calculateSimpleTrend(): Int {
        if (heartRateHistory.size < 3) return 50

        val firstHalf = heartRateHistory.take(heartRateHistory.size / 2).average()
        val secondHalf = heartRateHistory.drop(heartRateHistory.size / 2).average()
        val trendChange = secondHalf - firstHalf

        return when {
            trendChange > 10 -> 75   // 급격한 상승
            trendChange > 5 -> 60    // 상승
            trendChange > -5 -> 45   // 안정
            trendChange > -10 -> 35  // 하강 (회복)
            else -> 25               // 급격한 하강
        }
    }

    /**
     * 시간적 패턴 분석 (새로운 지표)
     */
    private fun calculateTemporalPatterns(): Int {
        if (timestampHistory.size < 3) return 50

        // 측정 간격의 일정성 확인
        val intervals = mutableListOf<Long>()
        for (i in 1 until timestampHistory.size) {
            intervals.add(timestampHistory[i] - timestampHistory[i-1])
        }

        val avgInterval = intervals.average()
        val intervalVariability = intervals.map { abs(it - avgInterval) }.average()

        // 최근 데이터의 급격한 변화 감지
        val recentChanges = mutableListOf<Double>()
        for (i in 1 until heartRateHistory.size) {
            recentChanges.add(abs(heartRateHistory[i] - heartRateHistory[i-1]))
        }

        val avgChange = recentChanges.average()

        val temporalStress = when {
            avgChange > 8 && intervalVariability < 2000 -> 80  // 급변하지만 규칙적
            avgChange > 5 -> 65                                // 중간 변화
            avgChange > 2 -> 45                                // 작은 변화
            else -> 30                                         // 매우 안정적
        }

        Log.d(TAG, "시간패턴 분석: 평균변화=$avgChange, 간격변동성=$intervalVariability, 점수=$temporalStress")
        return temporalStress
    }

    /**
     * 기존 메서드들 (기존 StressCalculator와 동일하지만 개선됨)
     */
    private fun calculateHeartRateTrend(): Int {
        if (heartRateHistory.size < 3) return 50

        val recentData = heartRateHistory.takeLast(7) // 더 많은 데이터 사용
        var trendScore = 0

        for (i in 1 until recentData.size) {
            val change = recentData[i] - recentData[i-1]
            when {
                change > 8 -> trendScore += 20   // 급격한 상승 (가중치 증가)
                change > 4 -> trendScore += 12   // 상승
                change > 1 -> trendScore += 6    // 약간 상승
                change > -1 -> trendScore += 3   // 안정
                change > -4 -> trendScore += 4   // 약간 하강
                change > -8 -> trendScore += 8   // 하강
                else -> trendScore += 15         // 급격한 하강
            }
        }

        val finalTrendScore = (trendScore * 1.5).toInt().coerceIn(0, 100)
        return finalTrendScore
    }

    private fun calculateHeartRateVariability(): Int {
        if (heartRateHistory.size < 3) return 50

        val mean = heartRateHistory.average()
        val variance = heartRateHistory.map { (it - mean).pow(2) }.average()
        val standardDeviation = sqrt(variance)

        val variabilityStress = when {
            standardDeviation > 18 -> 85  // 매우 높은 변동성
            standardDeviation > 12 -> 70  // 높은 변동성
            standardDeviation > 8 -> 55   // 보통 변동성
            standardDeviation > 4 -> 40   // 낮은 변동성
            else -> 25                    // 매우 낮은 변동성
        }

        return variabilityStress
    }

    private fun calculateRestingDeviation(currentHeartRate: Double): Int {
        val deviation = abs(currentHeartRate - userRestingHeartRate)
        val deviationPercentage = (deviation / userRestingHeartRate) * 100

        val deviationStress = when {
            deviationPercentage > 60 -> 95  // 60% 이상 편차
            deviationPercentage > 45 -> 80  // 45% 이상 편차
            deviationPercentage > 30 -> 65  // 30% 이상 편차
            deviationPercentage > 20 -> 50  // 20% 이상 편차
            deviationPercentage > 10 -> 35  // 10% 이상 편차
            else -> 20                      // 10% 미만 편차
        }

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

    /**
     * 상세 진단 정보 가져오기
     */
    fun getDetailedDiagnosis(): String {
        if (heartRateHistory.size < 3) {
            return "데이터가 부족합니다. 더 많은 측정이 필요합니다."
        }

        val currentHR = heartRateHistory.lastOrNull() ?: 0.0
        val avgHR = heartRateHistory.average()
        val hrvResult = ImprovedHRVCalculator.calculateImprovedHRV(heartRateHistory)
        val quality = ImprovedHRVCalculator.assessHRVQuality(heartRateHistory)

        return buildString {
            appendLine("=== 상세 스트레스 분석 ===")
            appendLine("현재 심박수: ${currentHR.toInt()} BPM")
            appendLine("평균 심박수: ${avgHR.toInt()} BPM")
            appendLine("RMSSD: ${hrvResult.rmssd.toInt()}ms")
            appendLine("SDNN: ${hrvResult.sdnn.toInt()}ms")
            appendLine("pNN50: ${hrvResult.pnn50.toInt()}%")
            appendLine("데이터 품질: $quality")
            appendLine("측정 기간: ${heartRateHistory.size * 10}초")
            appendLine("========================")
        }
    }
}