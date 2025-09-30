package com.ms.wearos.util

import android.util.Log
import kotlin.math.*

object ImprovedHRVCalculator {

    private const val TAG = "ImprovedHRV"

    /**
     * 심박수 기반으로 R-R 간격을 추정하여 더 정확한 HRV 계산
     * 실제 ECG R-R 간격과는 다르지만, 심박수만으로는 최선의 근사치
     */
    fun calculateImprovedHRV(heartRateHistory: List<Double>): HRVResult {
        if (heartRateHistory.size < 3) {
            return HRVResult(0.0, 0.0, 0.0, 50) // 기본값
        }

        // 1. 심박수를 R-R 간격으로 변환 (추정치)
        val rrIntervals = convertHeartRateToRRIntervals(heartRateHistory)

        // 2. 다양한 HRV 지표 계산
        val rmssd = calculateRMSSD(rrIntervals)
        val sdnn = calculateSDNN(rrIntervals)
        val pnn50 = calculatePNN50(rrIntervals)

        // 3. 종합 HRV 스코어 계산
        val hrvScore = calculateHRVScore(rmssd, sdnn, pnn50)

        Log.d(TAG, "HRV 계산 결과 - RMSSD: $rmssd, SDNN: $sdnn, pNN50: $pnn50, 스코어: $hrvScore")

        return HRVResult(rmssd, sdnn, pnn50, hrvScore)
    }

    /**
     * 심박수를 R-R 간격으로 변환 (밀리초 단위 추정)
     * 공식: RR간격(ms) = 60,000 / 심박수(BPM)
     */
    private fun convertHeartRateToRRIntervals(heartRates: List<Double>): List<Double> {
        return heartRates.map { hr ->
            if (hr > 0) {
                60000.0 / hr  // 밀리초 단위 R-R 간격 추정
            } else {
                1000.0  // 기본값
            }
        }
    }

    /**
     * RMSSD (Root Mean Square of Successive Differences) 계산
     * 연속된 R-R 간격의 차이에 대한 제곱평균제곱근
     */
    private fun calculateRMSSD(rrIntervals: List<Double>): Double {
        if (rrIntervals.size < 2) return 0.0

        val squaredDifferences = mutableListOf<Double>()

        for (i in 1 until rrIntervals.size) {
            val diff = rrIntervals[i] - rrIntervals[i-1]
            squaredDifferences.add(diff * diff)
        }

        val meanSquaredDiff = squaredDifferences.average()
        return sqrt(meanSquaredDiff)
    }

    /**
     * SDNN (Standard Deviation of NN intervals) 계산
     * 모든 R-R 간격의 표준편차
     */
    private fun calculateSDNN(rrIntervals: List<Double>): Double {
        if (rrIntervals.isEmpty()) return 0.0

        val mean = rrIntervals.average()
        val variance = rrIntervals.map { (it - mean).pow(2) }.average()
        return sqrt(variance)
    }

    /**
     * pNN50 계산
     * 연속된 R-R 간격의 차이가 50ms 이상인 비율 (%)
     */
    private fun calculatePNN50(rrIntervals: List<Double>): Double {
        if (rrIntervals.size < 2) return 0.0

        var count = 0
        var total = 0

        for (i in 1 until rrIntervals.size) {
            val diff = abs(rrIntervals[i] - rrIntervals[i-1])
            if (diff > 50.0) {  // 50ms 이상
                count++
            }
            total++
        }

        return if (total > 0) (count.toDouble() / total) * 100 else 0.0
    }

    /**
     * HRV 지표들을 종합하여 스트레스 점수 계산
     * 높은 HRV = 낮은 스트레스, 낮은 HRV = 높은 스트레스
     */
    private fun calculateHRVScore(rmssd: Double, sdnn: Double, pnn50: Double): Int {
        // 각 지표별 정상 범위 기반 점수 계산
        val rmssdScore = when {
            rmssd > 50 -> 20   // 매우 좋음 (낮은 스트레스)
            rmssd > 30 -> 35   // 좋음
            rmssd > 20 -> 50   // 보통
            rmssd > 10 -> 70   // 나쁨
            else -> 90         // 매우 나쁨 (높은 스트레스)
        }

        val sdnnScore = when {
            sdnn > 60 -> 20    // 매우 좋음
            sdnn > 40 -> 35    // 좋음
            sdnn > 25 -> 50    // 보통
            sdnn > 15 -> 70    // 나쁨
            else -> 90         // 매우 나쁨
        }

        val pnn50Score = when {
            pnn50 > 15 -> 20   // 매우 좋음
            pnn50 > 8 -> 35    // 좋음
            pnn50 > 3 -> 50    // 보통
            pnn50 > 1 -> 70    // 나쁨
            else -> 90         // 매우 나쁨
        }

        // 가중 평균 (RMSSD가 가장 중요한 지표)
        val finalScore = (rmssdScore * 0.5 + sdnnScore * 0.3 + pnn50Score * 0.2).toInt()

        return finalScore.coerceIn(0, 100)
    }

    /**
     * 연령대별 HRV 정상 범위 고려
     */
    fun getAgeAdjustedHRVScore(age: Int, baseScore: Int): Int {
        val ageAdjustment = when {
            age < 25 -> -5    // 젊은 사람은 일반적으로 HRV가 높음
            age < 35 -> 0     // 기준
            age < 45 -> +3    // 중년은 약간 조정
            age < 55 -> +7    // 더 큰 조정
            else -> +10       // 노년층은 자연스럽게 HRV가 낮아짐
        }

        return (baseScore + ageAdjustment).coerceIn(0, 100)
    }

    /**
     * HRV 계산 결과를 담는 데이터 클래스
     */
    data class HRVResult(
        val rmssd: Double,      // RMSSD 값
        val sdnn: Double,       // SDNN 값
        val pnn50: Double,      // pNN50 값 (%)
        val stressScore: Int    // 최종 스트레스 점수 (0-100)
    )

    /**
     * HRV 품질 평가 (측정 신뢰도 체크)
     */
    fun assessHRVQuality(heartRateHistory: List<Double>): HRVQuality {
        val dataPoints = heartRateHistory.size
        val dataRange = if (heartRateHistory.isNotEmpty()) {
            heartRateHistory.maxOrNull()!! - heartRateHistory.minOrNull()!!
        } else 0.0

        val quality = when {
            dataPoints < 3 -> HRVQuality.POOR
            dataPoints < 5 -> HRVQuality.FAIR
            dataPoints < 8 && dataRange > 5 -> HRVQuality.GOOD
            dataPoints >= 8 && dataRange > 3 -> HRVQuality.EXCELLENT
            else -> HRVQuality.FAIR
        }

        Log.d(TAG, "HRV 품질 평가: $quality (데이터 포인트: $dataPoints, 범위: $dataRange)")
        return quality
    }

    enum class HRVQuality {
        POOR,       // 신뢰도 낮음
        FAIR,       // 보통
        GOOD,       // 좋음
        EXCELLENT   // 매우 좋음
    }
}