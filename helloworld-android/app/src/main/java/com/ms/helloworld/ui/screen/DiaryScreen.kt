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
    navController: NavHostController
) {
    val backgroundColor = Color(0xFFF5F5F5)

    // 현재 임신 정보
    val currentWeek = PregnancyWeek(week = 32, dayCount = 5)

    // 일주일 일기 상태 (1일부터 7일까지)
    val weeklyDiaryStatus = listOf(
        DiaryStatus(1, true, true),   // 둘 다 씀
        DiaryStatus(2, true, false),  // 산모만 씀
        DiaryStatus(3, false, true),  // 남편만 씀
        DiaryStatus(4, false, false), // 아무도 안 씀
        DiaryStatus(5, false, false), // 아무도 안 씀
        DiaryStatus(6, false, false), // 아무도 안 씀
        DiaryStatus(7, false, false)  // 아무도 안 씀
    )

    // 산모 데이터
    val momData = MomData(
        weight = 62f,
        weightChange = 8f,
        sleepHours = 8f,
        targetSleepHours = 7.2f,
        condition = "좋음"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
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

        // 산모 데이터 요약 카드
        MomDataSummaryCard(
            momData = momData,
            onAddDataClick = {
                // 데이터 추가 화면으로 이동
                navController.navigate("add_data")
            }
        )
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
        Text(
            text = "${currentWeek.week}주차 (${currentWeek.dayCount}일째)",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

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
    onAddDataClick: () -> Unit
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "산모 데이터 요약",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                IconButton(
                    onClick = onAddDataClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "데이터 추가",
                        tint = Color.Black
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