package com.ms.helloworld.ui.screen

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ms.helloworld.R
import com.ms.helloworld.ui.components.CustomTopAppBar
import com.ms.helloworld.ui.theme.MainColor
import com.ms.helloworld.viewmodel.DiaryViewModel
import com.ms.helloworld.viewmodel.HomeViewModel
import com.ms.helloworld.viewmodel.HealthViewModel

// 데이터 클래스들
data class PregnancyWeek(
    val week: Int,
    val dayCount: Int
)

data class DiaryStatus(
    val day: Int,
    val momWritten: Boolean,
    val dadWritten: Boolean
)

data class MomHealthData(
    val weight: Float,        // 체중 (kg)
    val weightChange: Float,  // 체중 변화 (kg)
    val bloodPressureSystolic: Int,   // 수축기 혈압
    val bloodPressureDiastolic: Int,  // 이완기 혈압
    val bloodSugar: Int       // 혈당 (mg/dL)
)


enum class DiaryState {
    NONE,      // 아무것도 안 씀 - 회색
    MOM_ONLY,  // 산모만 씀 - F49699
    DAD_ONLY,  // 남편만 씀 - 88A9F8
    BOTH       // 둘 다 씀 - BCFF8F (체크 표시)
}

@SuppressLint("NewApi")
@Composable
fun DiaryScreen(
    navController: NavHostController,
    viewModel: DiaryViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel(),
    healthViewModel: HealthViewModel = hiltViewModel()
) {

    val backgroundColor = Color(0xFFF5F5F5)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val homeState by homeViewModel.momProfile.collectAsState()
    val currentPregnancyDay by homeViewModel.currentPregnancyDay.collectAsState()
    val menstrualDate by homeViewModel.menstrualDate.collectAsState()
    val healthState by healthViewModel.state.collectAsStateWithLifecycle()

    // 현재 보여지는 주차를 별도로 관리
    var viewingWeek by remember { mutableStateOf<Int?>(null) }

    // 스크린이 시작될 때 HomeViewModel과 HealthViewModel 데이터 로딩
    LaunchedEffect(Unit) {
        homeViewModel.forceRefreshProfile()
        homeViewModel.refreshProfile()
        homeViewModel.refreshCalendarEvents()
        healthViewModel.loadHealthHistory()
    }

    // 화면 진입/복귀 시 현재 주차로 리셋 (DiaryDetailScreen에서 돌아올 때)
    LaunchedEffect(navController.currentBackStackEntry) {
        viewingWeek = null
        viewModel.clearDiaries()
    }

    // 실제 임신 정보 사용 (currentPregnancyDay를 우선 사용)
    val actualCurrentWeek = homeState?.let { profile ->
        Log.d(
            "DiaryScreen",
            "MomProfile 데이터: 주차=${profile.pregnancyWeek}, 기존currentDay=${profile.currentDay}, 닉네임=${profile.nickname}"
        )
        val calculatedWeek = ((currentPregnancyDay - 1) / 7) + 1
        PregnancyWeek(
            week = calculatedWeek,
            dayCount = currentPregnancyDay  // HomeViewModel의 정확한 계산값 사용
        )
    } ?: run {
        val calculatedWeek = ((currentPregnancyDay - 1) / 7) + 1
        PregnancyWeek(week = calculatedWeek, dayCount = currentPregnancyDay)
    }

    // 표시할 주차 결정: viewingWeek가 설정되어 있으면 그것을 사용, 아니면 현재 주차
    val displayWeek = if (viewingWeek != null) {
        PregnancyWeek(week = viewingWeek!!, dayCount = currentPregnancyDay)
    } else {
        actualCurrentWeek
    }

    // API에서 받은 주간 일기 상태를 기존 형식으로 변환
    val weeklyDiaryStatus = state.weeklyDiaryStatus.map { weeklyStatus ->
        DiaryStatus(
            day = weeklyStatus.day,
            momWritten = weeklyStatus.momWritten,
            dadWritten = weeklyStatus.dadWritten
        )
    }.takeIf { it.isNotEmpty() } ?: listOf(
        // 기본값 (로딩 중이거나 데이터 없을 때)
        DiaryStatus(1, false, false),
        DiaryStatus(2, false, false),
        DiaryStatus(3, false, false),
        DiaryStatus(4, false, false),
        DiaryStatus(5, false, false),
        DiaryStatus(6, false, false),
        DiaryStatus(7, false, false)
    )

    // 현재 주차의 건강 데이터 계산 (HealthStatusScreen 로직 참고)
    val weekStartDay = if (actualCurrentWeek.week > 0) {
        (actualCurrentWeek.week - 1) * 7 + 1
    } else {
        if (currentPregnancyDay > 1) {
            val currentWeek = ((currentPregnancyDay - 1) / 7) + 1
            (currentWeek - 1) * 7 + 1
        } else 1
    }
    val weekEndDay = weekStartDay + 6

    // 건강 데이터를 HealthStatusScreen과 동일한 방식으로 변환
    val healthDataList = if (menstrualDate == null) {
        emptyList()
    } else {
            val historyDataMap = mutableMapOf<String, com.ms.helloworld.dto.response.MaternalHealthItem>()
            val todayDataMap = mutableMapOf<String, com.ms.helloworld.dto.response.MaternalHealthGetResponse>()

            healthState.healthHistory.forEach { item ->
                historyDataMap[item.recordDate] = item
            }

            healthState.todayHealthData?.let { todayData ->
                todayDataMap[todayData.recordDate] = todayData
            }

            val weeklyData = mutableListOf<com.ms.helloworld.ui.screen.HealthData>()
            for (day in weekStartDay..weekEndDay) {
                val targetDate = try {
                    val lmpDate = java.time.LocalDate.parse(menstrualDate)
                    lmpDate.plusDays((day - 1).toLong())
                } catch (e: Exception) {
                    null
                }

                val targetDateString = targetDate?.toString()
                val historyData = targetDateString?.let { historyDataMap[it] }
                val todayData = targetDateString?.let { todayDataMap[it] }

                when {
                    historyData != null -> {
                        val bloodPressure = healthViewModel.parseBloodPressure(historyData.bloodPressure)
                        weeklyData.add(com.ms.helloworld.ui.screen.HealthData(
                            day = ((day - weekStartDay) % 7) + 1,
                            weight = historyData.weight.toFloat(),
                            bloodPressureHigh = bloodPressure?.first?.toFloat(),
                            bloodPressureLow = bloodPressure?.second?.toFloat(),
                            bloodSugar = historyData.bloodSugar.toFloat(),
                            recordDate = historyData.recordDate
                        ))
                    }
                    todayData != null -> {
                        val bloodPressure = healthViewModel.parseBloodPressure(todayData.bloodPressure)
                        weeklyData.add(com.ms.helloworld.ui.screen.HealthData(
                            day = ((day - weekStartDay) % 7) + 1,
                            weight = todayData.weight.toFloat(),
                            bloodPressureHigh = bloodPressure?.first?.toFloat(),
                            bloodPressureLow = bloodPressure?.second?.toFloat(),
                            bloodSugar = todayData.bloodSugar.toFloat(),
                            recordDate = todayData.recordDate
                        ))
                    }
                    else -> {
                        weeklyData.add(com.ms.helloworld.ui.screen.HealthData(
                            day = ((day - weekStartDay) % 7) + 1,
                            weight = null,
                            bloodPressureHigh = null,
                            bloodPressureLow = null,
                            bloodSugar = null,
                            recordDate = targetDateString
                        ))
                    }
                }
            }
            weeklyData.toList()
        }

        // 건강 데이터 평균값 계산 (HealthStatusScreen의 calculateAverage 함수 사용)
        val avgWeight = healthDataList.mapNotNull { it.weight }.let { weights ->
            if (weights.isNotEmpty()) weights.average().toFloat() else 62f
        }
        val avgBloodPressureSystolic = healthDataList.mapNotNull { it.bloodPressureHigh }.let { pressures ->
            if (pressures.isNotEmpty()) pressures.average().toInt() else 120
        }
        val avgBloodPressureDiastolic = healthDataList.mapNotNull { it.bloodPressureLow }.let { pressures ->
            if (pressures.isNotEmpty()) pressures.average().toInt() else 80
        }
        val avgBloodSugar = healthDataList.mapNotNull { it.bloodSugar }.let { sugars ->
            if (sugars.isNotEmpty()) sugars.average().toInt() else 95
        }

        // 체중 변화 계산 (첫 번째 데이터 대비 최신 데이터)
        val weightChange = healthDataList.mapNotNull { it.weight }.let { weights ->
            if (weights.size >= 2) {
                weights.last() - weights.first()
            } else {
                0f
            }
        }

        // 산모 건강 데이터 (실제 데이터 기반)
        val momHealthData = MomHealthData(
            weight = avgWeight,
            weightChange = weightChange,
            bloodPressureSystolic = avgBloodPressureSystolic,
            bloodPressureDiastolic = avgBloodPressureDiastolic,
            bloodSugar = avgBloodSugar
        )

    // HomeViewModel의 실제 데이터를 DiaryViewModel에 전달
    LaunchedEffect(menstrualDate) {
        val actualMenstrualDate = menstrualDate
        if (actualMenstrualDate != null) {
            viewModel.setLmpDate(actualMenstrualDate)
        }
    }

    // HomeViewModel에서 임신 주차가 업데이트될 때 DiaryViewModel 새로고침
    LaunchedEffect(currentPregnancyDay, menstrualDate) {
        homeState?.let { profile ->
            val actualMenstrualDate = menstrualDate
            if (actualMenstrualDate != null) {
                val calculatedWeek = ((currentPregnancyDay - 1) / 7) + 1
                viewModel.setLmpDate(actualMenstrualDate)
                viewModel.loadWeeklyDiaries(calculatedWeek)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
            CustomTopAppBar(
                title = "${actualCurrentWeek.week}주차 (${actualCurrentWeek.dayCount}일째)",
                navController = navController
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp),
            ) {

                // 주차 네비게이션 헤더
                WeekNavigationHeader(
                    currentWeek = displayWeek,
                    actualCurrentWeek = actualCurrentWeek.week,
                    onPreviousWeek = {
                        if (displayWeek.week > 1) {
                            viewingWeek = displayWeek.week - 1
                            // 이전 데이터 클리어 후 새로운 데이터 로드
                            viewModel.clearDiaries()
                            viewModel.loadWeeklyDiaries(displayWeek.week - 1)
                        }
                    },
                    onNextWeek = {
                        if (displayWeek.week < actualCurrentWeek.week) {
                            viewingWeek = displayWeek.week + 1
                            // 이전 데이터 클리어 후 새로운 데이터 로드
                            viewModel.clearDiaries()
                            viewModel.loadWeeklyDiaries(displayWeek.week + 1)
                        }
                    },
                    onCurrentWeek = {
                        viewingWeek = null
                        // 이전 데이터 클리어 후 새로운 데이터 로드
                        viewModel.clearDiaries()
                        viewModel.loadWeeklyDiaries(actualCurrentWeek.week)
                    }
                )

                // 일주일 일기 체크 카드
                WeeklyDiaryCard(
                    weeklyStatus = weeklyDiaryStatus,
                    onDayClick = { dayInWeek ->
                        // 표시 중인 주차의 일수를 실제 임신 일수로 변환
                        val actualDay = (displayWeek.week - 1) * 7 + dayInWeek

                        navController.navigate("diary_detail/$actualDay")
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 산모 데이터 요약 카드
                MomDataSummaryCard(
                    momHealthData = momHealthData,
                    onCardClick = {
                        // HealthStatusScreen으로 이동
                        navController.navigate("health_status")
                    }
                )
            }
        }
    }

@Composable
fun WeeklyDiaryCard(
    weeklyStatus: List<DiaryStatus>,
    onDayClick: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = "일기",
                    modifier = Modifier.size(22.dp),
                    tint = MainColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "일주일 일기 체크",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 요일 라벨 (1-7)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(7) { index ->
                    Text(
                        text = (index + 1).toString(),
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 일기 상태 원들
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                weeklyStatus.forEach { status ->
                    DiaryStatusCircle(
                        status = status,
                        onClick = { onDayClick(status.day) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun DiaryStatusCircle(
    status: DiaryStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val diaryState = when {
        status.momWritten && status.dadWritten -> DiaryState.BOTH
        status.momWritten -> DiaryState.MOM_ONLY
        status.dadWritten -> DiaryState.DAD_ONLY
        else -> DiaryState.NONE
    }

    val circleColor = when (diaryState) {
        DiaryState.NONE -> Color(0xFFE0E0E0)      // 회색
        DiaryState.MOM_ONLY -> MainColor.copy(alpha = 0.9f)  // 산모만
        DiaryState.DAD_ONLY -> Color(0xFF88A9F8)  // 남편만
        DiaryState.BOTH -> Color(0xFF9CCC65)     // 둘 다
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(circleColor)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (diaryState == DiaryState.BOTH) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "완료",
                    modifier = Modifier.size(20.dp),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun MomDataSummaryCard(
    momHealthData: MomHealthData,
    onCardClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
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
                    text = "산모 데이터 요약",
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
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = "더보기",
                        modifier = Modifier.size(16.dp),
                        tint = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 체중
            DataSummaryItem(
                icon = R.drawable.ic_kg,
                iconColor =  Color(0xFFAED581),
                title = "체중",
                value = "${momHealthData.weight.toInt()}kg",
                subtitle = "",
                progress = momHealthData.weight / 150, // 200kg 기준으로 진행률 계산
                progressColor =  Color(0xFFAED581)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 혈압
            DataSummaryItem(
                icon = R.drawable.ic_heart_img,
                iconColor = Color(0xFFF49699),
                title = "혈압",
                value = "${momHealthData.bloodPressureSystolic}/${momHealthData.bloodPressureDiastolic}mmHg",
                subtitle = "",
                progress = momHealthData.bloodPressureSystolic / 200f, // 200mmHg 기준으로 진행률 계산
                progressColor = Color(0xFFF49699)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 혈당
            DataSummaryItem(
                icon = R.drawable.ic_blood_sugar,
                iconColor = Color.Unspecified,
                title = "혈당",
                value = "${momHealthData.bloodSugar}mg/dL",
                subtitle = "",
                progress = momHealthData.bloodSugar/ 200f, // 200mg/dL 기준으로 진행률 계산
                progressColor = Color(0xFF88A9F8)
            )
        }
    }
}

@Composable
fun DataSummaryItem(
    icon: Int,
    iconColor: Color,
    title: String,
    value: String,
    subtitle: String,
    progress: Float,
    progressColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 아이콘
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if(title == "혈당") Color(0xFF88A9F8).copy(alpha = 0.1f) else iconColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = title,
                modifier = Modifier.size(20.dp),
                tint = iconColor
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 정보
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Text(
                    text = value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
            }

            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // 진행률 바 (컨디션 제외)
            if (progress > 0f) {
                Spacer(modifier = Modifier.height(8.dp))
                // LinearProgressIndicator 대신 커스텀 프로그래스 바 사용
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFE0E0E0))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(progressColor)
                    )
                }
            }
        }
    }
}

@Composable
fun WeekNavigationHeader(
    currentWeek: PregnancyWeek,
    actualCurrentWeek: Int,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onCurrentWeek: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // 주차 네비게이션
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                /// 이전 주차 버튼 (조건부 표시)
                if (currentWeek.week > 1) {
                    IconButton(onClick = onPreviousWeek) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "이전 주차",
                            tint = Color.Black
                        )
                    }
                } else {
                    // 빈 공간으로 균형 맞추기
                    Spacer(modifier = Modifier.width(48.dp))
                }

                // 현재 주차 표시 (항상 중앙에 고정)
                Text(
                    text = "${currentWeek.week}주차",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                // 다음 주차 버튼 (조건부 표시)
                if (currentWeek.week < actualCurrentWeek) {
                    IconButton(onClick = onNextWeek) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "다음 주차",
                            tint = Color.Black
                        )
                    }
                } else {
                    // 빈 공간으로 균형 맞추기
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }
            // 현재 주차가 아닌 경우 "현재로 돌아가기" 버튼 (타이틀 아래에 표시)
            if (currentWeek.week != actualCurrentWeek) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = onCurrentWeek) {
                        Text(
                            text = "현재 주차로 이동 (${actualCurrentWeek}주차)",
                            fontSize = 14.sp,
                            color = Color(0xFFF49699)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DiaryScreenPreview() {
    DiaryScreen(navController = null as NavHostController)
}