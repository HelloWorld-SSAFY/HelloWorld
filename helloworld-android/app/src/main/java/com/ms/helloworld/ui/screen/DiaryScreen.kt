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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ms.helloworld.dto.response.MomProfile
import com.ms.helloworld.ui.components.CustomTopAppBar
import com.ms.helloworld.viewmodel.DiaryViewModel
import com.ms.helloworld.viewmodel.HomeViewModel

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
    homeViewModel: HomeViewModel = hiltViewModel()
) {

    val backgroundColor = Color(0xFFF5F5F5)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val homeState by homeViewModel.momProfile.collectAsState()
    val currentPregnancyDay by homeViewModel.currentPregnancyDay.collectAsState()
    val menstrualDate by homeViewModel.menstrualDate.collectAsState()

    // 스크린이 시작될 때 HomeViewModel 데이터 로딩
    LaunchedEffect(Unit) {

        // 데이터가 초기 상태이면 강제 새로고침
        if (homeState.nickname == "로딩중") {
            homeViewModel.forceRefreshProfile()
        } else {
            homeViewModel.refreshProfile()
        }
    }

    // 실제 임신 정보 사용 (currentPregnancyDay를 우선 사용)
    val currentWeek = homeState?.let { profile ->
        Log.d("DiaryScreen", "MomProfile 데이터: 주차=${profile.pregnancyWeek}, 기존currentDay=${profile.currentDay}, 닉네임=${profile.nickname}")

    val homeState by actualHomeViewModel.momProfile.collectAsState()
    val currentPregnancyDay by actualHomeViewModel.currentPregnancyDay.collectAsState()
    val coupleId by actualHomeViewModel.coupleId.collectAsState()
    val menstrualDate by actualHomeViewModel.menstrualDate.collectAsState()
    val userId by actualHomeViewModel.userId.collectAsState()
    val userGender by actualHomeViewModel.userGender.collectAsState()

    // 현재 보여지는 주차를 별도로 관리
    var viewingWeek by remember { mutableStateOf<Int?>(null) }

    // 실제 임신 정보 사용 (currentPregnancyDay를 우선 사용)
    val actualCurrentWeek = homeState?.let { profile ->
        println("📊 DiaryScreen - MomProfile 데이터: 주차=${profile.pregnancyWeek}, 기존currentDay=${profile.currentDay}, 닉네임=${profile.nickname}")
        println("📊 DiaryScreen - HomeViewModel currentPregnancyDay: ${currentPregnancyDay}")
        println("📊 DiaryScreen - homeState 객체 해시: ${profile.hashCode()}")
        PregnancyWeek(
            week = profile.pregnancyWeek,
            dayCount = currentPregnancyDay  // HomeViewModel의 정확한 계산값 사용
        )
    } ?: run {
        PregnancyWeek(week = 1, dayCount = currentPregnancyDay)
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

    // 산모 건강 데이터 (임시 - 추후 HealthData API와 연동)
    val momHealthData = MomHealthData(
        weight = 62f,
        weightChange = 8f,
        bloodPressureSystolic = 120,
        bloodPressureDiastolic = 80,
        bloodSugar = 95
    )

    // HomeViewModel의 실제 데이터를 DiaryViewModel에 전달
    LaunchedEffect(menstrualDate) {
        val actualMenstrualDate = menstrualDate
        if (actualMenstrualDate != null) {
            viewModel.setLmpDate(actualMenstrualDate)
        }
    }

    // 사용자 정보를 DiaryViewModel에 전달
    LaunchedEffect(userId, userGender) {
        if (userId != null && userGender != null) {
            println("👤 DiaryScreen - DiaryViewModel에 사용자 정보 전달: userId=$userId, userGender=$userGender")
            viewModel.setUserInfo(userId, userGender)

            // 사용자 정보가 업데이트되면 기존 데이터를 다시 처리
            homeState?.let { profile ->
                println("🔄 DiaryScreen - 사용자 정보 업데이트 후 주간 일기 재로딩")
                viewModel.loadWeeklyDiaries(profile.pregnancyWeek)
            }
        }
    }

    // HomeViewModel에서 임신 주차가 업데이트될 때 DiaryViewModel 새로고침
    LaunchedEffect(homeState?.pregnancyWeek, menstrualDate) {
        homeState?.let { profile ->
            val actualMenstrualDate = menstrualDate
            if (actualMenstrualDate != null) {
                viewModel.setLmpDate(actualMenstrualDate)
                viewModel.loadWeeklyDiaries(profile.pregnancyWeek)
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
                        println("📅 DiaryScreen - 이전 주차로 이동: ${displayWeek.week - 1}주차")
                        viewModel.loadWeeklyDiaries(displayWeek.week - 1)
                    }
                },
                onNextWeek = {
                    if (displayWeek.week < actualCurrentWeek.week) {
                        viewingWeek = displayWeek.week + 1
                        println("📅 DiaryScreen - 다음 주차로 이동: ${displayWeek.week + 1}주차")
                        viewModel.loadWeeklyDiaries(displayWeek.week + 1)
                    }
                },
                onCurrentWeek = {
                    viewingWeek = null
                    println("📅 DiaryScreen - 현재 주차로 돌아가기: ${actualCurrentWeek.week}주차")
                    viewModel.loadWeeklyDiaries(actualCurrentWeek.week)
                }
            )

            // 일주일 일기 체크 카드
            WeeklyDiaryCard(
                weeklyStatus = weeklyDiaryStatus,
                onDayClick = { dayInWeek ->
                    // 표시 중인 주차의 일수를 실제 임신 일수로 변환
                    val actualDay = (displayWeek.week - 1) * 7 + dayInWeek
                    println("🔗 DiaryScreen - 네비게이션: ${displayWeek.week}주차 dayInWeek=$dayInWeek -> actualDay=$actualDay")
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
fun PregnancyWeekHeader(
    currentWeek: PregnancyWeek,
    onWeekListClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // 주차 리스트로 이동 버튼 (선택사항)
        TextButton(
            onClick = onWeekListClick,
            colors = ButtonDefaults.textButtonColors(
                contentColor = Color.Gray
            )
        ) {
            Text(
                text = "다른 주차 보기",
                fontSize = 12.sp
            )
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = "주차 리스트",
                modifier = Modifier.size(16.dp)
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
                    modifier = Modifier.size(20.dp),
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "일주일 일기 체크",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

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
        DiaryState.MOM_ONLY -> Color(0xFFF49699)  // 산모만
        DiaryState.DAD_ONLY -> Color(0xFF88A9F8)  // 남편만
        DiaryState.BOTH -> Color(0xFFBCFF8F)      // 둘 다
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
                icon = Icons.Default.Person,
                iconColor = Color(0xFFFF9800),
                title = "체중",
                value = "${momHealthData.weight.toInt()}kg",
                subtitle = "+${momHealthData.weightChange.toInt()}kg",
                progress = momHealthData.weight / 100f, // 100kg 기준으로 진행률 계산
                progressColor = Color(0xFFFF9800)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 혈압
            DataSummaryItem(
                icon = Icons.Default.Favorite,
                iconColor = Color(0xFFE91E63),
                title = "혈압",
                value = "${momHealthData.bloodPressureSystolic}/${momHealthData.bloodPressureDiastolic}",
                subtitle = "mmHg",
                progress = momHealthData.bloodPressureSystolic / 200f, // 200mmHg 기준으로 진행률 계산
                progressColor = Color(0xFFE91E63)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 혈당
            DataSummaryItem(
                icon = Icons.Default.Face,
                iconColor = Color(0xFF2196F3),
                title = "혈당",
                value = "${momHealthData.bloodSugar}mg/dL",
                subtitle = "정상범위 70-140",
                progress = momHealthData.bloodSugar / 200f, // 200mg/dL 기준으로 진행률 계산
                progressColor = Color(0xFF2196F3)
            )
        }
    }
}

@Composable
fun DataSummaryItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                .background(iconColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
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
                    fontWeight = FontWeight.Bold,
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
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = progressColor,
                    trackColor = Color(0xFFE0E0E0)
                )
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
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 주차 네비게이션
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 이전 주차 버튼
                IconButton(
                    onClick = onPreviousWeek,
                    enabled = currentWeek.week > 1
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "이전 주차",
                        tint = if (currentWeek.week > 1) Color.Black else Color.Gray
                    )
                }

                // 현재 주차 표시
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${currentWeek.week}주차",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    // 현재 주차가 아닌 경우 "현재로 돌아가기" 버튼
                    if (currentWeek.week != actualCurrentWeek) {
                        TextButton(
                            onClick = onCurrentWeek,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = "현재 주차로 (${actualCurrentWeek}주차)",
                                fontSize = 12.sp,
                                color = Color(0xFFF49699)
                            )
                        }
                    }
                }

                // 다음 주차 버튼
                IconButton(
                    onClick = onNextWeek,
                    enabled = currentWeek.week < actualCurrentWeek
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "다음 주차",
                        tint = if (currentWeek.week < actualCurrentWeek) Color.Black else Color.Gray
                    )
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