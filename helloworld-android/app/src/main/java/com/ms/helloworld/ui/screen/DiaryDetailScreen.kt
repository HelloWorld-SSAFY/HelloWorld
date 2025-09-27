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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.navigation.NavHostController
import com.ms.helloworld.navigation.Screen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ms.helloworld.viewmodel.HomeViewModel
import com.ms.helloworld.viewmodel.DiaryViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import android.util.Log

// 데이터 클래스들
data class DiaryEntry(
    val title: String,
    val content: String,
    val date: String,
    val imageUrl: String? = null
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
    initialDay: Int = -1  // -1은 현재 날짜 사용을 의미
) {

    // HomeViewModel에서 모든 데이터 가져오기
    val homeViewModel: HomeViewModel = hiltViewModel()
    val momProfile by homeViewModel.momProfile.collectAsState()
    val userGender by homeViewModel.userGender.collectAsState()
    val userId by homeViewModel.userId.collectAsState()
    val coupleId by homeViewModel.coupleId.collectAsState()
    val menstrualDate by homeViewModel.menstrualDate.collectAsState()
    val currentPregnancyDay by homeViewModel.currentPregnancyDay.collectAsState()

    // DiaryViewModel에서 일별 일기 데이터 가져오기 - Activity 레벨에서 동일한 인스턴스 사용
    val diaryViewModel: DiaryViewModel = hiltViewModel()
    val diaryState by diaryViewModel.state.collectAsStateWithLifecycle()

    // 현재 주차의 총 일수 (1주 = 7일, 하지만 임신 마지막 주는 더 적을 수 있음)
    val totalDaysInWeek = 7

    // 현재 주차의 시작일과 끝일 계산 (UI 표시용)
    val weekStartDay = if (momProfile?.pregnancyWeek != null && momProfile.pregnancyWeek > 0) {
        val calculated = (momProfile.pregnancyWeek - 1) * 7 + 1
        Log.d("DiaryDetailScreen", "weekStartDay 계산 (momProfile 사용): pregnancyWeek=${momProfile.pregnancyWeek} -> $calculated")
        calculated
    } else {
        // 로딩 중일 때는 currentPregnancyDay 기준으로 계산
        if (currentPregnancyDay > 1) {
            val currentWeek = ((currentPregnancyDay - 1) / 7) + 1
            val calculated = (currentWeek - 1) * 7 + 1
            Log.d("DiaryDetailScreen", "weekStartDay 계산 (currentPregnancyDay 사용): currentPregnancyDay=$currentPregnancyDay, currentWeek=$currentWeek -> $calculated")
            calculated
        } else {
            Log.d("DiaryDetailScreen", "weekStartDay 계산: 기본값 1 사용")
            1
        }
    }
    val weekEndDay = weekStartDay + 6
    Log.d("DiaryDetailScreen", "주차 범위: ${weekStartDay}일 ~ ${weekEndDay}일")

    // 현재 표시할 일차를 상태로 관리 (네비게이션 없이 내부에서 변경)
    var currentViewingDay by remember { mutableStateOf(
        if (initialDay == -1) {
            // 기본값: 현재 실제 임신 일수 사용, 하지만 현재 주차를 벗어나지 않도록 제한
            if (currentPregnancyDay > 1) {
                val calculated = minOf(currentPregnancyDay, weekEndDay)
                Log.d("DiaryDetailScreen", "currentViewingDay 계산: initialDay=$initialDay, currentPregnancyDay=$currentPregnancyDay, weekEndDay=$weekEndDay -> $calculated")
                calculated
            } else {
                Log.d("DiaryDetailScreen", "currentViewingDay 계산: weekStartDay=$weekStartDay (currentPregnancyDay=$currentPregnancyDay <= 1)")
                weekStartDay
            }
        } else {
            // 특정 일수가 지정된 경우 해당 값 사용
            Log.d("DiaryDetailScreen", "currentViewingDay 계산: initialDay=$initialDay 사용")
            initialDay
        }
    ) }

    // 현재 보고 있는 날짜의 주차 계산
    val viewingWeek = remember(currentViewingDay) {
        val calculatedWeek = ((currentViewingDay - 1) / 7) + 1
        Log.d("DiaryDetailScreen", "주차 계산: currentViewingDay=$currentViewingDay -> ${calculatedWeek}주차")
        calculatedWeek
    }

    // actualDayNumber는 currentViewingDay를 사용
    val actualDayNumber = currentViewingDay

    // 현재 선택된 주차 내 위치 (UI 표시용)
    var currentDayInWeek by remember { mutableStateOf(1) }

    // DiaryDetailScreen에서 HomeViewModel 데이터를 먼저 로드
    LaunchedEffect(Unit) {
        Log.d("DiaryDetailScreen", "HomeViewModel 데이터 로드 시작")
        homeViewModel.refreshProfile()
    }

    // HomeViewModel 데이터 로딩 상태 확인
    LaunchedEffect(coupleId, menstrualDate, userId, userGender) {
        Log.d("DiaryDetailScreen", "HomeViewModel 데이터 변경:")
        Log.d("DiaryDetailScreen", "  - coupleId: $coupleId")
        Log.d("DiaryDetailScreen", "  - menstrualDate: $menstrualDate")
        Log.d("DiaryDetailScreen", "  - userId: $userId")
        Log.d("DiaryDetailScreen", "  - userGender: $userGender")
    }
    // actualDayNumber가 업데이트되면 currentDayInWeek도 업데이트
    LaunchedEffect(actualDayNumber) {
        if (actualDayNumber > 1) {
            currentDayInWeek = ((actualDayNumber - 1) % 7) + 1
        }
    }


    // HomeViewModel의 실제 데이터를 DiaryViewModel에 전달
    LaunchedEffect(menstrualDate) {
        val actualMenstrualDate = menstrualDate
        if (actualMenstrualDate != null) {
            diaryViewModel.setLmpDate(actualMenstrualDate)
        }
    }

    // coupleId는 서버에서 토큰으로 자동 처리됨
    val getLmpDate = { menstrualDate ?: "2025-01-18" } // menstrualDate 사용 (HomeViewModel과 동일한 기본값)

    // 필수 데이터 부족 시 재로딩
    LaunchedEffect(currentViewingDay) {
        if (coupleId == null || menstrualDate == null) {
            Log.d("DiaryDetailScreen", "필수 데이터 부족, HomeViewModel 재로딩 시도")
            homeViewModel.refreshProfile()
        }
    }

    // 일별 일기 데이터 로드 - currentViewingDay 변경 시 재로드
    LaunchedEffect(currentViewingDay, coupleId, menstrualDate) {
        // 날짜 변경 시 즉시 이전 데이터 초기화
        diaryViewModel.clearDiaries()

        Log.d("DiaryDetailScreen", "API 호출 조건 체크:")
        Log.d("DiaryDetailScreen", "  - actualDayNumber: $actualDayNumber (>= 1: ${actualDayNumber >= 1})")
        Log.d("DiaryDetailScreen", "  - coupleId: $coupleId (not null: ${coupleId != null})")
        Log.d("DiaryDetailScreen", "  - menstrualDate: $menstrualDate (not null: ${menstrualDate != null})")

        if (actualDayNumber >= 1 && coupleId != null && menstrualDate != null) {
            // 날짜 계산 디버깅 추가
            val lmpDateString = getLmpDate()
            try {
                val lmpDate = java.time.LocalDate.parse(lmpDateString)
                val calculatedDate = lmpDate.plusDays((actualDayNumber - 1).toLong())
                Log.d("DiaryDetailScreen", "날짜 계산 확인:")
                Log.d("DiaryDetailScreen", "  - LMP: $lmpDateString")
                Log.d("DiaryDetailScreen", "  - 임신일수: ${actualDayNumber}일차")
                Log.d("DiaryDetailScreen", "  - 계산식: LMP + ${actualDayNumber-1}일 (수정됨)")
                Log.d("DiaryDetailScreen", "  - 계산된 날짜: $calculatedDate")
                Log.d("DiaryDetailScreen", "  - 오늘 날짜: ${java.time.LocalDate.now()}")
            } catch (e: Exception) {
                Log.e("DiaryDetailScreen", "날짜 계산 오류: ${e.message}")
            }

            Log.d("DiaryDetailScreen", "API 호출 시작: ${actualDayNumber}일차")
            diaryViewModel.loadDiariesByDay(
                day = actualDayNumber,
                lmpDate = getLmpDate()
            )
        } else {
            Log.d("DiaryDetailScreen", "데이터 로딩 대기 중 (조건 미충족)")
        }
    }

    // 화면이 다시 나타날 때 새로고침 (일기 등록 후 돌아올 때)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
//                println("🔄 DiaryDetailScreen - 화면 복귀, 일기 새로고침")
//                println("  - actualDayNumber: $actualDayNumber")

                if (actualDayNumber > 0) {
                    // 일별 일기 조회
                    diaryViewModel.loadDiariesByDay(
                        day = actualDayNumber,
                        lmpDate = getLmpDate()
                    )
                }
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
        // 디버깅: 각 일기의 role inference 확인
        apiDiaries.forEachIndexed { index, diary ->
            val inferredRole = diary.inferAuthorRole(userId, userGender)
            println("🔍 DiaryDetailScreen - Diary[$index]: ID=${diary.diaryId}, authorId=${diary.authorId}, authorRole=${diary.authorRole}, inferredRole=${inferredRole}")
            println("🔍 현재 사용자: userId=$userId, userGender=$userGender")
        }

        val birthDiary = apiDiaries.find {
            diary -> diary.inferAuthorRole(userId, userGender, null, null) == "FEMALE"  // TODO: 커플 정보 전달 필요
        }?.let { diary ->
            Log.d("DiaryDetailScreen", "✅ 출산일기 찾음:")
            Log.d("DiaryDetailScreen", "  - 제목: ${diary.diaryTitle}")
            Log.d("DiaryDetailScreen", "  - targetDate: ${diary.targetDate}")
            Log.d("DiaryDetailScreen", "  - 요청한 임신일수: ${actualDayNumber}일차")
            DiaryEntry(
                title = diary.diaryTitle ?: "",
                content = diary.diaryContent ?: "",
                date = diary.targetDate,
                imageUrl = diary.thumbnailUrl
            )
        }
        val observationDiary = apiDiaries.find {
            diary -> diary.inferAuthorRole(userId, userGender, null, null) == "MALE"  // TODO: 커플 정보 전달 필요
        }?.let { diary ->
            Log.d("DiaryDetailScreen", "✅ 관찰일기 찾음:")
            Log.d("DiaryDetailScreen", "  - 제목: ${diary.diaryTitle}")
            Log.d("DiaryDetailScreen", "  - targetDate: ${diary.targetDate}")
            Log.d("DiaryDetailScreen", "  - 요청한 임신일수: ${actualDayNumber}일차")
            DiaryEntry(
                title = diary.diaryTitle ?: "",
                content = diary.diaryContent ?: "",
                date = diary.targetDate,
                imageUrl = diary.thumbnailUrl
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
                    if (viewingWeek > 0) {
                        Text(
                            text = "${viewingWeek}주차",
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

            // 일자 네비게이션 (actualDayNumber가 유효할 때 표시)
            if (actualDayNumber >= 1 && viewingWeek > 0) {
                DayNavigationHeader(
                    currentDay = actualDayNumber,
                    currentDayInWeek = currentDayInWeek,
                    totalDaysInWeek = totalDaysInWeek,
                    canGoPrevious = actualDayNumber > weekStartDay,
                    canGoNext = actualDayNumber < weekEndDay, // 주차 내 전체 날짜 이동 허용
                    onPreviousDay = {
                        // 현재 주차 내에서 이전 날로 이동 (상태 변경만, 네비게이션 없음)
                        if (actualDayNumber > weekStartDay) {
                            currentViewingDay = actualDayNumber - 1
                        }
                    },
                    onNextDay = {
                        // 현재 주차 내에서 다음 날로 이동 (상태 변경만, 네비게이션 없음)
                        if (actualDayNumber < weekEndDay) { // currentPregnancyDay 제한 제거
                            currentViewingDay = actualDayNumber + 1
                        }
                    }
                )
            } else {
                // 로딩 중 표시
                Text(
                    text = "데이터 로딩 중...",
                    modifier = Modifier.padding(16.dp),
                    color = Color.Gray
                )
            }

            // 출산일기 섹션
            DiarySection(
                title = "출산일기",
                diary = currentDiary.birthDiary,
                borderColor = Color(0xFFF49699),
                canAddOrEdit = userGender?.lowercase() == "female", // 여성만 출산일기 작성/수정 가능
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
                    // DiaryBoardScreen과 동일하게 편집할 일기 데이터를 DiaryViewModel에 설정
                    Log.d("DiaryDetailScreen", "🚨 출산일기 수정 버튼 클릭!")
                    Log.d("DiaryDetailScreen", "apiDiaries.size: ${apiDiaries.size}")
                    Log.d("DiaryDetailScreen", "userId: $userId, userGender: $userGender")

                    apiDiaries.forEachIndexed { index, diary ->
                        val role = diary.inferAuthorRole(userId, userGender, null, null)
                        Log.d("DiaryDetailScreen", "Diary[$index]: ID=${diary.diaryId}, authorRole=${diary.authorRole}, inferredRole=$role")
                    }

                    val birthDiaryData = apiDiaries.find { diary ->
                        diary.inferAuthorRole(userId, userGender, null, null) == "FEMALE"
                    }
                    Log.d("DiaryDetailScreen", "찾은 출산일기: $birthDiaryData")

                    birthDiaryData?.let { diary ->
                        Log.d("DiaryDetailScreen", "🔍 일기 정보 확인:")
                        Log.d("DiaryDetailScreen", "  - diaryId: ${diary.diaryId}")
                        Log.d("DiaryDetailScreen", "  - authorId: ${diary.authorId}")
                        Log.d("DiaryDetailScreen", "  - authorRole: ${diary.authorRole}")
                        Log.d("DiaryDetailScreen", "  - 현재 userId: $userId")
                        Log.d("DiaryDetailScreen", "  - 작성자 일치: ${diary.authorId == userId}")

                        Log.d("DiaryDetailScreen", "setEditingDiary 호출: diaryId=${diary.diaryId}")
                        diaryViewModel.setEditingDiary(diary)

                        navController.navigate(
                            Screen.DiaryRegisterScreen.createRoute(
                                diaryType = "birth",
                                day = actualDayNumber,
                                isEdit = true
                            ) + "?diaryId=${diary.diaryId}"
                        )
                    } ?: Log.w("DiaryDetailScreen", "출산일기를 찾을 수 없습니다!")
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
                canAddOrEdit = userGender?.lowercase() == "male", // 남성만 관찰일기 작성/수정 가능
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
                    // DiaryBoardScreen과 동일하게 편집할 일기 데이터를 DiaryViewModel에 설정
                    Log.d("DiaryDetailScreen", "🚨 관찰일기 수정 버튼 클릭!")
                    Log.d("DiaryDetailScreen", "apiDiaries.size: ${apiDiaries.size}")
                    Log.d("DiaryDetailScreen", "userId: $userId, userGender: $userGender")

                    val observationDiaryData = apiDiaries.find { diary ->
                        diary.inferAuthorRole(userId, userGender, null, null) == "MALE"
                    }
                    Log.d("DiaryDetailScreen", "찾은 관찰일기: $observationDiaryData")

                    observationDiaryData?.let { diary ->
                        Log.d("DiaryDetailScreen", "setEditingDiary 호출: diaryId=${diary.diaryId}")
                        diaryViewModel.setEditingDiary(diary)

                        navController.navigate(
                            Screen.DiaryRegisterScreen.createRoute(
                                diaryType = "observation",
                                day = actualDayNumber,
                                isEdit = true
                            ) + "?diaryId=${diary.diaryId}"
                        )
                    } ?: Log.w("DiaryDetailScreen", "관찰일기를 찾을 수 없습니다!")
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
    canGoPrevious: Boolean = true,
    canGoNext: Boolean = true,
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
            enabled = canGoPrevious
        ) {
            Icon(
                Icons.Default.KeyboardArrowLeft,
                contentDescription = "이전 날",
                modifier = Modifier.size(28.dp),
                tint = if (canGoPrevious) Color.Black else Color.Gray
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
            enabled = canGoNext
        ) {
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = "다음 날",
                modifier = Modifier.size(28.dp),
                tint = if (canGoNext) Color.Black else Color.Gray
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
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onClick() },
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 왼쪽: 썸네일 공간 - 실제 이미지 또는 placeholder
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(120.dp)
                .background(
                    Color.Gray.copy(alpha = 0.1f),
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (diary.imageUrl != null && diary.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = diary.imageUrl,
                    contentDescription = "일기 썸네일",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = "📸",
                    fontSize = 16.sp,
                    color = Color.Gray
                )
            }
        }

        // 오른쪽: 제목, 내용, 날짜
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
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
                    maxLines = 4,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            // 작성 날짜 (하단 우측)
            Text(
                text = diary.date,
                fontSize = 11.sp,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.End)
            )
        }
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