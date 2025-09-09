package com.ms.helloworld.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.ms.helloworld.navigation.Screen

// 데이터 클래스들
data class DiaryEntry(
    val title: String,
    val content: String,
    val date: String
)

data class DailyDiary(
    val day: Int,
    val birthDiary: DiaryEntry?, // 출산일기
    val observationDiary: DiaryEntry? // 관찰일기
)

@SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryDetailScreen(
    navController: NavHostController,
    initialDay: Int = 1
) {
    val backgroundColor = Color(0xFFF5F5F5)
    var currentDay by remember { mutableStateOf(initialDay) }

    // 샘플 데이터 (실제로는 Repository나 ViewModel에서 가져올 데이터)
    val diaryData = remember {
        mutableStateMapOf<Int, DailyDiary>(
            1 to DailyDiary(
                day = 1,
                birthDiary = DiaryEntry(
                    title = "첫 날 기록",
                    content = "오늘은 정말 특별한 날이었어요. 아기의 움직임을 더 자주 느낄 수 있었고, 남편과 함께 아기 용품을 준비하는 시간이 행복했습니다.",
                    date = "2024-01-01"
                ),
                observationDiary = null
            ),
            2 to DailyDiary(
                day = 2,
                birthDiary = null,
                observationDiary = DiaryEntry(
                    title = "몸의 변화",
                    content = "오늘은 입덧이 조금 있었지만 전반적으로 컨디션이 좋았어요. 체중도 적정 수준을 유지하고 있고, 수면의 질도 개선되고 있는 것 같습니다.",
                    date = "2024-01-02"
                )
            )
        )
    }

    val currentDiary = diaryData[currentDay] ?: DailyDiary(currentDay, null, null)

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${currentDay}일차 일기",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = Color.Black
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // 일자 네비게이션
        DayNavigationHeader(
            currentDay = currentDay,
            onPreviousDay = {
                if (currentDay > 1) currentDay--
            },
            onNextDay = {
                if (currentDay < 7) currentDay++
            }
        )

        // 출산일기 섹션
        DiarySection(
            title = "출산일기",
            diary = currentDiary.birthDiary,
            borderColor = Color(0xFFF49699),
            onAddClick = {
                // 출산일기 작성 화면으로 이동
                navController.navigate(
                    Screen.DiaryRegisterScreen.createRoute(
                        diaryType = "birth",
                        day = currentDay,
                        isEdit = false
                    )
                )            },
            onEditClick = {
                // 출산일기 수정 화면으로 이동
                navController.navigate(
                    Screen.DiaryRegisterScreen.createRoute(
                        diaryType = "birth",
                        day = currentDay,
                        isEdit = true
                    )
                )
            }
        )

        // 관찰일기 섹션
        DiarySection(
            title = "관찰일기",
            diary = currentDiary.observationDiary,
            borderColor = Color(0xFF88A9F8),
            onAddClick = {
                // 관찰일기 작성 화면으로 이동
                navController.navigate(
                    Screen.DiaryRegisterScreen.createRoute(
                        diaryType = "observation",
                        day = currentDay,
                        isEdit = false
                    )
                )
            },
            onEditClick = {
                // 관찰일기 수정 화면으로 이동
                navController.navigate(
                    Screen.DiaryRegisterScreen.createRoute(
                        diaryType = "observation",
                        day = currentDay,
                        isEdit = true
                    )
                )
            }
        )
        }
    }
}

@Composable
fun DayNavigationHeader(
    currentDay: Int,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onPreviousDay,
            enabled = currentDay > 1
        ) {
            Icon(
                Icons.Default.KeyboardArrowLeft,
                contentDescription = "이전 날",
                modifier = Modifier.size(28.dp),
                tint = if (currentDay > 1) Color.Black else Color.Gray
            )
        }

        Text(
            text = "${currentDay}일차",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            textAlign = TextAlign.Center
        )

        IconButton(
            onClick = onNextDay,
            enabled = currentDay < 7
        ) {
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = "다음 날",
                modifier = Modifier.size(28.dp),
                tint = if (currentDay < 7) Color.Black else Color.Gray
            )
        }
    }
}

@Composable
fun DiarySection(
    title: String,
    diary: DiaryEntry?,
    borderColor: Color,
    onAddClick: () -> Unit,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // 헤더 (제목 + 추가 버튼)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = borderColor
                )

                IconButton(
                    onClick = if (diary != null) onEditClick else onAddClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        if (diary != null) Icons.Default.Edit else Icons.Default.Add,
                        contentDescription = if (diary != null) "수정" else "추가",
                        modifier = Modifier.size(20.dp),
                        tint = borderColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 일기 내용 또는 빈 상태
            if (diary != null) {
                // 일기가 있는 경우
                DiaryContent(diary = diary)
            } else {
                // 일기가 없는 경우
                EmptyDiaryState()
            }
        }
    }
}

@Composable
fun DiaryContent(diary: DiaryEntry) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 일기 제목
        if (diary.title.isNotEmpty()) {
            Text(
                text = diary.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // 일기 내용
        Text(
            text = diary.content,
            fontSize = 13.sp,
            color = Color.Black,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )

        // 작성 날짜
        Text(
            text = diary.date,
            fontSize = 11.sp,
            color = Color.Gray,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

@Composable
fun EmptyDiaryState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "아직 작성이 되어있지 않아요",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

// 일기 작성/수정 화면으로 이동하는 함수들 (실제 구현시 사용)
@Composable
fun WriteDiaryScreen(
    navController: NavHostController,
    diaryType: String, // "birth" 또는 "observation"
    day: Int,
    isEdit: Boolean = false
) {
    // 일기 작성/수정 화면 구현
    // 이 부분은 별도로 구현하시면 됩니다

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = if (isEdit) "${if (diaryType == "birth") "출산" else "관찰"}일기 수정"
            else "${if (diaryType == "birth") "출산" else "관찰"}일기 작성",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 실제 작성 폼은 여기에 구현
        Text(
            text = "${day}일차 일기 작성 화면입니다.",
            fontSize = 14.sp,
            color = Color.Gray
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DiaryDetailScreenPreview() {
    DiaryDetailScreen(navController = null as NavHostController, initialDay = 1)
}