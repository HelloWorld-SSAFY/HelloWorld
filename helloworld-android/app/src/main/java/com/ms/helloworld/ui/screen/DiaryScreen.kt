package com.ms.helloworld.ui.screen

import android.annotation.SuppressLint
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

data class MomData(
    val weight: Float,
    val weightChange: Float,
    val sleepHours: Float,
    val targetSleepHours: Float,
    val condition: String
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
    homeViewModel: HomeViewModel? = null
) {
    // homeViewModel이 전달되지 않으면 기본 hiltViewModel 사용
    val actualHomeViewModel = homeViewModel ?: hiltViewModel<HomeViewModel>()
    println("📱 DiaryScreen - HomeViewModel 인스턴스: ${actualHomeViewModel.hashCode()}")
    println("📱 DiaryScreen - 전달받은 HomeViewModel: ${homeViewModel?.hashCode() ?: "null"}")

    val backgroundColor = Color(0xFFF5F5F5)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val homeState by actualHomeViewModel.momProfile.collectAsState()

    // 실제 임신 정보 사용
    val currentWeek = homeState?.let { profile ->
        println("📊 DiaryScreen - MomProfile 데이터: 주차=${profile.pregnancyWeek}, 계산된일차=${profile.currentDay}, 닉네임=${profile.nickname}")
        println("📊 DiaryScreen - homeState 객체 해시: ${profile.hashCode()}")
        PregnancyWeek(
            week = profile.pregnancyWeek,
            dayCount = profile.currentDay
        )
    } ?: run {
        println("⚠️ DiaryScreen - homeState가 null, 기본값 사용")
        PregnancyWeek(week = 1, dayCount = 1)
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

    // 산모 데이터 (임시 - 추후 HealthData API와 연동)
    val momData = MomData(
        weight = 62f,
        weightChange = 8f,
        sleepHours = 8f,
        targetSleepHours = 7.2f,
        condition = "좋음"
    )

    // HomeViewModel에서 임신 주차가 업데이트될 때 DiaryViewModel 새로고침
    LaunchedEffect(homeState?.pregnancyWeek) {
        homeState?.let { profile ->
            println("🔄 DiaryScreen - 임신 주차 변경 감지: ${profile.pregnancyWeek}주차")
            viewModel.loadWeeklyDiaries(profile.pregnancyWeek)
        }
    }

    // 디버깅: HomeState 변경사항 추적
    LaunchedEffect(homeState) {
        if (homeState == null) {
            println("📊 DiaryScreen - HomeState가 null입니다")
        } else {
            println("📊 DiaryScreen - LaunchedEffect HomeState 업데이트: 주차=${homeState.pregnancyWeek}, 닉네임=${homeState.nickname}, currentDay=${homeState.currentDay}")
            println("📊 DiaryScreen - homeState 객체 해시: ${homeState.hashCode()}")
        }
    }

    // StateFlow 값 직접 확인
    LaunchedEffect(Unit) {
        println("📊 DiaryScreen - HomeViewModel StateFlow 직접 확인: ${actualHomeViewModel.momProfile.value.pregnancyWeek}주차")
    }

    // 에러 메시지 표시
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            println("❌ DiaryScreen - 에러: $message")
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        CustomTopAppBar(
            title = "${currentWeek.week}주차 (${currentWeek.dayCount}일째)",
            navController = navController
        ).also {
            println("🏷️ DiaryScreen TopAppBar - 표시 중: ${currentWeek.week}주차 (${currentWeek.dayCount}일째)")
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp),
        ) {

            // 임신 주차 헤더
            PregnancyWeekHeader(
                currentWeek = currentWeek,
                onWeekListClick = {
                    // 주차 리스트로 이동
                    navController.navigate("week_list")
                }
            )

            // 일주일 일기 체크 카드
            WeeklyDiaryCard(
                weeklyStatus = weeklyDiaryStatus,
                onDayClick = { day ->
                    // 특정 일자 일기로 이동
                    navController.navigate("diary_detail/$day")
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 산모 데이터 요약 카드
            MomDataSummaryCard(
                momData = momData,
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
    momData: MomData,
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
                icon = Icons.Default.Check,
                iconColor = Color(0xFFFF9800),
                title = "체중",
                value = "${momData.weight.toInt()}kg",
                subtitle = "+${momData.weightChange.toInt()}kg",
                progress = 0.7f, // 임의의 진행률
                progressColor = Color(0xFFFF9800)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 수면시간
            DataSummaryItem(
                icon = Icons.Default.AccountBox,
                iconColor = Color(0xFF4CAF50),
                title = "수면시간",
                value = "${momData.sleepHours.toInt()}시간",
                subtitle = "평균 ${momData.targetSleepHours}시간",
                progress = momData.sleepHours / 10f, // 10시간 기준
                progressColor = Color(0xFF4CAF50)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 컨디션
            DataSummaryItem(
                icon = Icons.Default.Face,
                iconColor = Color(0xFFFFEB3B),
                title = "컨디션",
                value = momData.condition,
                subtitle = "",
                progress = 0f, // 컨디션은 진행률 표시 안함
                progressColor = Color.Transparent
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

@Preview(showBackground = true)
@Composable
fun DiaryScreenPreview() {
    DiaryScreen(navController = null as NavHostController)
}