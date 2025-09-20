package com.ms.helloworld.ui.screen

import android.annotation.SuppressLint
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
import com.ms.helloworld.ui.components.CustomTopAppBar
import androidx.hilt.navigation.compose.hiltViewModel
import com.ms.helloworld.viewmodel.HomeViewModel
import com.ms.helloworld.viewmodel.DiaryViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner

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

    // HomeViewModel에서 임신 주차 정보 가져오기
    val homeViewModel: HomeViewModel = hiltViewModel()
    val momProfile by homeViewModel.momProfile.collectAsState()
    val userGender by homeViewModel.userGender.collectAsState()

    // 디버깅을 위한 로그
    LaunchedEffect(userGender) {
        println("🚻 DiaryDetailScreen - 사용자 성별: $userGender")
        println("🚻 DiaryDetailScreen - 출산일기 버튼 표시: ${userGender?.lowercase() == "female"}")
        println("🚻 DiaryDetailScreen - 관찰일기 버튼 표시: ${userGender?.lowercase() == "male"}")
    }

    // DiaryViewModel에서 일별 일기 데이터 가져오기
    val diaryViewModel: DiaryViewModel = hiltViewModel()
    val diaryState by diaryViewModel.state.collectAsStateWithLifecycle()

    // 현재 주차의 총 일수 (1주 = 7일)
    val totalDaysInWeek = 7

    // 현재 주차의 시작일과 끝일 계산
    val weekStartDay = (momProfile.pregnancyWeek - 1) * 7 + 1
    val weekEndDay = momProfile.pregnancyWeek * 7

    // 현재 선택된 날 (주차 내에서의 상대적 위치)
    var currentDayInWeek by remember { mutableStateOf(initialDay.coerceIn(1, totalDaysInWeek)) }

    // 실제 임신 일수 계산 (전체 임신 기간에서의 절대적 위치)
    val actualDayNumber = weekStartDay + currentDayInWeek - 1

    // TODO: SharedPreferences나 DataStore에서 실제 사용자 정보 가져오기
    val getCoupleId = { 1L } // 임시로 하드코딩
    val getLmpDate = { "2025-02-02" } // 임시로 하드코딩 (스웨거와 동일)

    // 일별 일기 데이터 로드
    LaunchedEffect(actualDayNumber) {
        // day API 호출: calendar/diary/day
        println("📆 DiaryDetailScreen - 일별 일기 로드")
        println("  - actualDayNumber: ${actualDayNumber}일차")
        println("  - pregnancyWeek: ${momProfile.pregnancyWeek}주차")
        println("  - currentDayInWeek: $currentDayInWeek")
        println("  - coupleId: ${getCoupleId()}")
        println("  - lmpDate: ${getLmpDate()}")

        // 임시 테스트: 작은 day 값으로 테스트
        val testDay = if (actualDayNumber > 100) {
            (actualDayNumber % 280) + 1 // 임신 기간 내로 조정
        } else {
            actualDayNumber
        }

        println("📝 임시 테스트 - 원본 day: $actualDayNumber, 조정된 day: $testDay")

        diaryViewModel.loadDiariesByDay(
            coupleId = getCoupleId(),
            day = testDay,
            lmpDate = getLmpDate()
        )
    }

    // 화면이 다시 나타날 때 새로고침 (일기 등록 후 돌아올 때)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                println("🔄 DiaryDetailScreen - 화면 복귀, 일기 새로고침")

                // 디버깅용: 전체 일기 조회
                diaryViewModel.loadAllDiariesForDebug()

                // 일별 일기 조회
                diaryViewModel.loadDiariesByDay(
                    coupleId = getCoupleId(),
                    day = actualDayNumber,
                    lmpDate = getLmpDate()
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // API에서 로드된 일기 데이터 사용
    val apiDiaries = diaryState.diaries

    // API 데이터를 DailyDiary 형식으로 변환
    val currentDiary = if (apiDiaries.isNotEmpty()) {
        val birthDiary = apiDiaries.find { it.authorRole == "FEMALE" }?.let { diary ->
            DiaryEntry(
                title = diary.diaryTitle ?: "",
                content = diary.diaryContent ?: "",
                date = diary.targetDate
            )
        }
        val observationDiary = apiDiaries.find { it.authorRole == "MALE" }?.let { diary ->
            DiaryEntry(
                title = diary.diaryTitle ?: "",
                content = diary.diaryContent ?: "",
                date = diary.targetDate
            )
        }
        DailyDiary(
            day = actualDayNumber,
            birthDiary = birthDiary,
            observationDiary = observationDiary
        )
    } else {
        DailyDiary(actualDayNumber, null, null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // 커스텀 TopAppBar with 임신 주차 정보
        TopAppBar(
            title = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 48.dp), // navigationIcon 크기만큼 오른쪽 패딩 추가
                    contentAlignment = Alignment.Center
                ) {
                    if (momProfile.pregnancyWeek > 0) {
                        Text(
                            text = "${momProfile.pregnancyWeek}주차",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = "출산일기",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "뒤로가기"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.White,
                titleContentColor = Color.Black,
                navigationIconContentColor = Color.Black
            )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 일자 네비게이션
            DayNavigationHeader(
                currentDay = actualDayNumber,
                currentDayInWeek = currentDayInWeek,
                totalDaysInWeek = totalDaysInWeek,
                onPreviousDay = {
                    if (currentDayInWeek > 1) currentDayInWeek--
                },
                onNextDay = {
                    if (currentDayInWeek < totalDaysInWeek) currentDayInWeek++
                }
            )

            // 출산일기 섹션
            DiarySection(
                title = "출산일기",
                diary = currentDiary.birthDiary,
                borderColor = Color(0xFFF49699),
                canAddOrEdit = userGender?.lowercase() == "female" || userGender == null, // 여성만 출산일기 작성/수정 가능 (로딩 중에는 모두 표시)
                onAddClick = {
                    // 출산일기 작성 화면으로 이동
                    navController.navigate(
                        Screen.DiaryRegisterScreen.createRoute(
                            diaryType = "birth",
                            day = actualDayNumber,
                            isEdit = false
                        )
                    )
                },
                onEditClick = {
                    // 출산일기 수정 화면으로 이동
                    navController.navigate(
                        Screen.DiaryRegisterScreen.createRoute(
                            diaryType = "birth",
                            day = actualDayNumber,
                            isEdit = true
                        )
                    )
                },
                onContentClick = {
                    // DiaryBoardScreen으로 이동
                    navController.navigate(
                        Screen.DiaryBoardScreen.createRoute(
                            diaryType = "birth",
                            day = actualDayNumber
                        )
                    )
                }
            )

            // 관찰일기 섹션
            DiarySection(
                title = "관찰일기",
                diary = currentDiary.observationDiary,
                borderColor = Color(0xFF88A9F8),
                canAddOrEdit = userGender?.lowercase() == "male" || userGender == null, // 남성만 관찰일기 작성/수정 가능 (로딩 중에는 모두 표시)
                onAddClick = {
                    // 관찰일기 작성 화면으로 이동
                    navController.navigate(
                        Screen.DiaryRegisterScreen.createRoute(
                            diaryType = "observation",
                            day = actualDayNumber,
                            isEdit = false
                        )
                    )
                },
                onEditClick = {
                    // 관찰일기 수정 화면으로 이동
                    navController.navigate(
                        Screen.DiaryRegisterScreen.createRoute(
                            diaryType = "observation",
                            day = actualDayNumber,
                            isEdit = true
                        )
                    )
                },
                onContentClick = {
                    // DiaryBoardScreen으로 이동
                    navController.navigate(
                        Screen.DiaryBoardScreen.createRoute(
                            diaryType = "observation",
                            day = actualDayNumber
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
    currentDayInWeek: Int,
    totalDaysInWeek: Int,
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
            enabled = currentDayInWeek > 1
        ) {
            Icon(
                Icons.Default.KeyboardArrowLeft,
                contentDescription = "이전 날",
                modifier = Modifier.size(28.dp),
                tint = if (currentDayInWeek > 1) Color.Black else Color.Gray
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${currentDay}일차",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
            Text(
                text = "(${currentDayInWeek}/7일)",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }

        IconButton(
            onClick = onNextDay,
            enabled = currentDayInWeek < totalDaysInWeek
        ) {
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = "다음 날",
                modifier = Modifier.size(28.dp),
                tint = if (currentDayInWeek < totalDaysInWeek) Color.Black else Color.Gray
            )
        }
    }
}

@Composable
fun DiarySection(
    title: String,
    diary: DiaryEntry?,
    borderColor: Color,
    canAddOrEdit: Boolean = true,
    onAddClick: () -> Unit,
    onEditClick: () -> Unit,
    onContentClick: () -> Unit
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

                if (canAddOrEdit) {
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
                } else {
                    // 권한이 없을 때는 빈 공간으로 대체
                    Spacer(modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 일기 내용 또는 빈 상태
            if (diary != null) {
                // 일기가 있는 경우
                DiaryContent(
                    diary = diary,
                    onClick = onContentClick
                )
            } else {
                // 일기가 없는 경우
                EmptyDiaryState()
            }
        }
    }
}

@Composable
fun DiaryContent(
    diary: DiaryEntry,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onClick() }
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