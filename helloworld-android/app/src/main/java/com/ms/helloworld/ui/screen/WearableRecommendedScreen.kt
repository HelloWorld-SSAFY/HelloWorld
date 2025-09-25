package com.ms.helloworld.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ms.helloworld.R
import com.ms.helloworld.dto.response.StressLevel
import com.ms.helloworld.navigation.Screen
import com.ms.helloworld.ui.components.CustomTopAppBar
import com.ms.helloworld.viewmodel.WearableViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@SuppressLint("NewApi")
@Composable
fun WearableRecommendedScreen(
    navController: NavHostController,
    wearableViewModel: WearableViewModel = hiltViewModel()

) {
    val contractions by wearableViewModel.contractions.collectAsState()
    val fetalMovements by wearableViewModel.fetalMovements.collectAsState()
    val isLoadingContractions by wearableViewModel.isLoading.collectAsState()
    val isLoadingFetal by wearableViewModel.isLoadingFetal.collectAsState()

    // 실시간 스트레스 레벨 상태 추가
    val stressLevel by wearableViewModel.stressLevel.collectAsState()

    // 오늘 날짜로 진통 기록 로드
    LaunchedEffect(Unit) {
        val today = LocalDate.now()
        val todayString = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        // 이번 주 범위 계산 (일요일 ~ 토요일)
        val dayOfWeek = today.dayOfWeek.value % 7 // 월=1, 일=0
        val startOfWeek = today.minusDays(dayOfWeek.toLong())
        val endOfWeek = startOfWeek.plusDays(6)
        val weekStartString = startOfWeek.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val weekEndString = endOfWeek.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        // 오늘의 진통 기록 로드
        wearableViewModel.loadContractions(from = todayString, to = todayString)

        // 이번 주 태동 기록 로드
        wearableViewModel.loadFetalMovements(from = weekStartString, to = weekEndString)
    }

    // 계산된 값들
    val todayContractionsCount = contractions.size
    val weeklyFetalAverage = if (fetalMovements.isEmpty()) 0.0 else {
        fetalMovements.sumOf { it.totalCount }.toDouble() / fetalMovements.size
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        CustomTopAppBar(
            title = "wearable",
            navController = navController
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 실시간 상태 섹션
            RealTimeStatusSection(stressLevel = stressLevel)

            // 건강 지표 섹션
            HealthMetricsSection(viewModel = wearableViewModel)

            // 음악 추천 섹션
            MusicRecommendationSection(navController = navController)

            // 명상 추천 섹션
            MeditationRecommendationSection(navController = navController)

            // 요가 추천 섹션
            YogaRecommendationSection(navController = navController)

            // 나들이 추천 섹션
            OutdoorRecommendationSection(navController = navController)

            // 태동/진통 기록 섹션
            RecordSection(
                navController,
                todayContractionsCount = todayContractionsCount,
                weeklyFetalAverage = weeklyFetalAverage,
                isLoadingContractions = isLoadingContractions,
                isLoadingFetal = isLoadingFetal
            )

            // 하단 여백 추가하여 바텀 네비게이션 영역까지 스크롤 가능하도록
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun RealTimeStatusSection(stressLevel: StressLevel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
        ) {
            Text(
                text = "실시간 상태",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "스트레스 지수 : ",
                    fontSize = 20.sp,
                    color = Color.Gray
                )
                Text(
                    text = stressLevel.displayName,
                    fontSize = 20.sp,
                    color = stressLevel.color,
                    fontWeight = FontWeight.Medium
                )
            }

            // 추가: 스트레스 레벨별 메시지
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = getStressLevelMessage(stressLevel),
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

        }
    }
}

private fun getStressLevelMessage(stressLevel: StressLevel): String {
    return when (stressLevel) {
        StressLevel.LOW -> "컨디션이 매우 좋습니다"
        StressLevel.STABLE -> "안정적인 상태를 유지하고 있습니다"
        StressLevel.MEDIUM -> "적당한 휴식을 취해보세요"
        StressLevel.HIGH -> "충분한 휴식이 필요합니다"
        StressLevel.VERY_HIGH -> "즉시 휴식을 취하고 전문가와 상담하세요"
    }
}

@Composable
fun HealthMetricsSection(
    viewModel: WearableViewModel = hiltViewModel()
) {
    val wearableData by viewModel.wearableData.collectAsState()
    val isLoading by viewModel.isLoadingWearable.collectAsState()
    val error by viewModel.error.collectAsState()

    // 컴포넌트가 처음 로드될 때 데이터 가져오기
    LaunchedEffect(Unit) {
        viewModel.loadWearableData()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            when {
                isLoading -> {
                    // 로딩 상태
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF4DABF7)
                        )
                    }
                }
                error != null -> {
                    // 에러 상태
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "데이터를 불러올 수 없습니다",
                            color = Color.Red,
                            fontSize = 14.sp
                        )
                        Button(
                            onClick = { viewModel.loadWearableData() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4DABF7)
                            )
                        ) {
                            Text("다시 시도")
                        }
                    }
                }
                wearableData != null -> {
                    // 데이터가 있을 때
                    val heartRate = wearableData!!.heartrate.hr
                    val stressScore = wearableData!!.heartrate.stress
                    val steps = wearableData!!.step.steps

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CircularProgressChart(
                            percentage = calculateHeartRatePercentage(heartRate),
                            label = "심박수",
                            color = Color(0xFFFF6B6B),
                            value = "${heartRate}bpm"
                        )
                        CircularProgressChart(
                            percentage = stressScore.toFloat(),
                            label = "스트레스",
                            color = Color(0xFF4DABF7),
                            value = "${stressScore}점"
                        )
                        CircularProgressChart(
                            percentage = calculateStepsPercentage(steps),
                            label = "걸음수",
                            color = Color(0xFFFFD93D),
                            value = "${steps}걸음"
                        )
                    }
                }
                else -> {
                    // 초기 상태 또는 데이터 없음
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CircularProgressChart(
                            percentage = 0f,
                            label = "심박수",
                            color = Color(0xFFFF6B6B),
                            value = "--bpm"
                        )
                        CircularProgressChart(
                            percentage = 0f,
                            label = "스트레스",
                            color = Color(0xFF4DABF7),
                            value = "--점"
                        )
                        CircularProgressChart(
                            percentage = 0f,
                            label = "걸음수",
                            color = Color(0xFFFFD93D),
                            value = "--걸음"
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun CircularProgressChart(
    percentage: Float,
    label: String,
    color: Color,
    value: String = "${percentage.toInt()}%"
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(60.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = percentage / 100f,
                modifier = Modifier.fillMaxSize(),
                color = color,
                strokeWidth = 6.dp,
                strokeCap = StrokeCap.Round,
                trackColor = Color(0xFFE0E0E0)
            )
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun MusicRecommendationSection(
    navController: NavHostController
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    navController.navigate(Screen.MusicScreen.route)
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "음악 추천",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_music),
                    contentDescription = "음악",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(Screen.MusicScreen.route)},
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "마음을 가라앉히는 음악 듣기",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "더보기",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun MeditationRecommendationSection(navController: NavHostController) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { navController.navigate(Screen.MeditationScreen.route) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "명상 추천",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_meditation),
                    contentDescription = "명상",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(Screen.MeditationScreen.route) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "심신 안정에 도움이 되는 명상 시작하기",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "더보기",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun YogaRecommendationSection(
    navController: NavHostController
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { navController.navigate(Screen.YogaScreen.route) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "요가 추천",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_yoga),
                    contentDescription = "요가",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(Screen.YogaScreen.route) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "임산부를 위한 안전한 요가 동작 배우기",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "더보기",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun OutdoorRecommendationSection(navController : NavHostController) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable {navController.navigate(Screen.OutingScreen.route) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "나들이 추천",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_outdoor),
                    contentDescription = "나들이",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {navController.navigate(Screen.OutingScreen.route) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "오늘은 날씨가 좋아요. 가까운 공원에 가보세요.",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    lineHeight = 20.sp
                )
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "더보기",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun RecordSection(
    navController: NavHostController?,
    todayContractionsCount: Int,
    weeklyFetalAverage: Double,
    isLoadingContractions: Boolean,
    isLoadingFetal: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clickable {
                navController?.navigate(Screen.RecordDetailScreen.route)
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "기록 관리",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "더보기",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = "더보기",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.Top
            ) {
                // 태동 기록
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "태동 기록",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier.height(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingFetal) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFF6BB6FF),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = String.format("%.1f", weeklyFetalAverage),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6BB6FF)
                            )
                        }
                    }


                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "주간 평균",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                // 구분선
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(80.dp)
                        .background(Color(0xFFE0E0E0))
                )

                // 진통 기록
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "진통 기록",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier.fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingContractions) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFFFF6B9D),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "${todayContractionsCount}회",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF6B9D)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "오늘 총 횟수",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

// 심박수 퍼센트 계산 (60-100bpm을 정상 범위로 가정)
private fun calculateHeartRatePercentage(heartRate: Int): Float {
    // 일반적인 성인 심박수 범위: 60-100bpm
    // 60을 0%, 100을 100%로 계산
    return when {
        heartRate < 60 -> 0f
        heartRate > 100 -> 100f
        else -> ((heartRate - 60) / 40f) * 100f
    }.coerceIn(0f, 100f)
}

private fun calculateStepsPercentage(steps: Int): Float {
    // 하루 권장 걸음수 10,000보를 100%로 계산
    return (steps / 10000f * 100f).coerceIn(0f, 100f)
}

@Preview(showBackground = true)
@Composable
fun WearableRecommendedScreenPreview() {
    WearableRecommendedScreen(navController = null as NavHostController)
}