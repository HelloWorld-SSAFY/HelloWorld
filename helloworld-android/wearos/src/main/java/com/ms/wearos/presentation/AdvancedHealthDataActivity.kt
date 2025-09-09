// AdvancedHealthDataActivity.kt - 심박수와 활동량에 대한 상세 분석만 제공
package com.ms.wearos.presentation

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import kotlinx.coroutines.launch

@Composable
fun AdvancedHealthDataScreen(
    currentHeartRate: Double,
    currentSteps: Int,
    currentCalories: Double,
    currentDistance: Double
) {
    var heartRateHistory by remember { mutableStateOf(listOf<Double>()) }
    var stepsHistory by remember { mutableStateOf(listOf<Int>()) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisResult by remember { mutableStateOf("분석 결과가 여기에 표시됩니다") }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // 현재 데이터를 히스토리에 추가
    LaunchedEffect(currentHeartRate, currentSteps) {
        if (currentHeartRate > 0) {
            heartRateHistory = (heartRateHistory + currentHeartRate).takeLast(20)
        }
        if (currentSteps > 0) {
            stepsHistory = (stepsHistory + currentSteps).takeLast(20)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text(
            text = "건강 데이터 분석",
            style = MaterialTheme.typography.title3,
            color = MaterialTheme.colors.primary
        )

        // 현재 상태 요약
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = {}
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "현재 상태",
                    style = MaterialTheme.typography.caption1
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${currentHeartRate.toInt()}",
                            style = MaterialTheme.typography.title3,
                            color = getHeartRateColor(currentHeartRate)
                        )
                        Text("BPM", style = MaterialTheme.typography.caption2)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$currentSteps",
                            style = MaterialTheme.typography.title3,
                            color = getStepsColor(currentSteps)
                        )
                        Text("걸음", style = MaterialTheme.typography.caption2)
                    }
                }
            }
        }

        // 심박수 분석
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = {}
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "심박수 분석",
                    style = MaterialTheme.typography.caption1
                )

                if (heartRateHistory.isNotEmpty()) {
                    val avgHeartRate = heartRateHistory.average()
                    val maxHeartRate = heartRateHistory.maxOrNull() ?: 0.0
                    val minHeartRate = heartRateHistory.minOrNull() ?: 0.0

                    Text(
                        text = "평균: ${avgHeartRate.toInt()} BPM",
                        style = MaterialTheme.typography.body2
                    )
                    Text(
                        text = "최고: ${maxHeartRate.toInt()} / 최저: ${minHeartRate.toInt()}",
                        style = MaterialTheme.typography.caption2
                    )

                    Text(
                        text = getHeartRateZone(avgHeartRate),
                        style = MaterialTheme.typography.caption2,
                        color = getHeartRateColor(avgHeartRate)
                    )
                } else {
                    Text(
                        text = "데이터 수집 중...",
                        style = MaterialTheme.typography.caption2
                    )
                }
            }
        }

        // 활동량 분석
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = {}
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "활동량 분석",
                    style = MaterialTheme.typography.caption1
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${currentCalories.toInt()}",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.primary
                        )
                        Text("kcal", style = MaterialTheme.typography.caption2)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(currentDistance / 1000).format(2)}",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.primary
                        )
                        Text("km", style = MaterialTheme.typography.caption2)
                    }
                }

                Text(
                    text = getActivityLevel(currentSteps),
                    style = MaterialTheme.typography.caption2,
                    color = getStepsColor(currentSteps)
                )
            }
        }

        // 건강 권장사항
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = {}
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "건강 권장사항",
                    style = MaterialTheme.typography.caption1
                )

                Text(
                    text = analysisResult,
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurface
                )
            }
        }

        // 분석 버튼
        Button(
            onClick = {
                scope.launch {
                    isAnalyzing = true
                    analysisResult = analyzeHealthData(
                        heartRateHistory = heartRateHistory,
                        stepsHistory = stepsHistory,
                        currentCalories = currentCalories,
                        currentDistance = currentDistance
                    )
                    isAnalyzing = false
                }
            },
            enabled = !isAnalyzing && (heartRateHistory.isNotEmpty() || stepsHistory.isNotEmpty()),
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(if (isAnalyzing) "분석 중..." else "건강 상태 분석")
        }

        // 데이터 초기화 버튼
        Button(
            onClick = {
                heartRateHistory = emptyList()
                stepsHistory = emptyList()
                analysisResult = "분석 결과가 여기에 표시됩니다"
                Log.d("AdvancedHealth", "Health data history cleared")
            },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("데이터 초기화")
        }
    }
}

// 심박수 색상 결정
private fun getHeartRateColor(heartRate: Double): Color {
    return when {
        heartRate <= 0 -> Color.Gray
        heartRate < 60 -> Color.Blue
        heartRate < 100 -> Color.Green
        heartRate < 120 -> Color.Yellow
        else -> Color.Red
    }
}

// 걸음수 색상 결정
private fun getStepsColor(steps: Int): Color {
    return when {
        steps < 1000 -> Color.Red
        steps < 5000 -> Color.Yellow
        steps < 10000 -> Color.Green
        else -> Color.Blue
    }
}

// 심박수 존 결정
private fun getHeartRateZone(heartRate: Double): String {
    return when {
        heartRate <= 0 -> "측정 안됨"
        heartRate < 60 -> "휴식존"
        heartRate < 70 -> "지방연소존"
        heartRate < 85 -> "유산소존"
        heartRate < 95 -> "무산소존"
        else -> "최대존"
    }
}

// 활동 수준 결정
private fun getActivityLevel(steps: Int): String {
    return when {
        steps < 1000 -> "비활성"
        steps < 3000 -> "저활성"
        steps < 7000 -> "보통활성"
        steps < 10000 -> "활발함"
        else -> "매우활발함"
    }
}

// 건강 데이터 분석
private fun analyzeHealthData(
    heartRateHistory: List<Double>,
    stepsHistory: List<Int>,
    currentCalories: Double,
    currentDistance: Double
): String {
    val recommendations = mutableListOf<String>()

    // 심박수 분석
    if (heartRateHistory.isNotEmpty()) {
        val avgHeartRate = heartRateHistory.average()
        val heartRateVariability = if (heartRateHistory.size >= 2) {
            val differences = heartRateHistory.zipWithNext { a, b -> kotlin.math.abs(a - b) }
            differences.average()
        } else 0.0

        when {
            avgHeartRate > 100 -> recommendations.add("심박수가 높습니다. 휴식을 취하세요.")
            avgHeartRate < 60 -> recommendations.add("심박수가 낮습니다. 가벼운 운동을 권장합니다.")
            heartRateVariability < 5 -> recommendations.add("심박수가 안정적입니다.")
            else -> recommendations.add("정상적인 심박수 범위입니다.")
        }
    }

    // 활동량 분석
    if (stepsHistory.isNotEmpty()) {
        val avgSteps = stepsHistory.average().toInt()
        when {
            avgSteps < 3000 -> recommendations.add("활동량이 부족합니다. 더 많이 움직이세요.")
            avgSteps < 7000 -> recommendations.add("적당한 활동량입니다. 조금 더 활동해보세요.")
            avgSteps >= 10000 -> recommendations.add("훌륭한 활동량입니다. 계속 유지하세요.")
            else -> recommendations.add("좋은 활동량입니다.")
        }
    }

    // 칼로리 소모 분석
    when {
        currentCalories < 50 -> recommendations.add("칼로리 소모가 적습니다.")
        currentCalories > 300 -> recommendations.add("활발한 활동으로 많은 칼로리를 소모했습니다.")
        else -> recommendations.add("적절한 칼로리 소모입니다.")
    }

    // 거리 분석
    val distanceKm = currentDistance / 1000
    when {
        distanceKm < 1.0 -> recommendations.add("이동 거리가 짧습니다.")
        distanceKm > 5.0 -> recommendations.add("상당한 거리를 이동했습니다.")
        else -> recommendations.add("적당한 거리를 이동했습니다.")
    }

    return if (recommendations.isNotEmpty()) {
        recommendations.joinToString(" ")
    } else {
        "충분한 데이터가 없습니다. 더 많은 측정이 필요합니다."
    }
}