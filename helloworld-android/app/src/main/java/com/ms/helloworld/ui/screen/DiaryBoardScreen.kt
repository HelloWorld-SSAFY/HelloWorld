package com.ms.helloworld.ui.screen

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.ms.helloworld.viewmodel.HomeViewModel
import com.ms.helloworld.viewmodel.DiaryViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// 사진 타입
enum class PhotoType {
    ULTRASOUND, // 초음파 사진
    REGULAR     // 일반 사진
}

// 사진 데이터
data class DiaryPhoto(
    val id: String,
    val url: String,
    val type: PhotoType
)

// 일기 데이터
data class DiaryBoardData(
    val title: String,
    val content: String,
    val photos: List<DiaryPhoto>,
    val date: String,
    val diaryType: String // "birth" 또는 "observation"
)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("NewApi")
@Composable
fun DiaryBoardScreen(
    navController: NavHostController,
    diaryType: String, // "birth" 또는 "observation"
    day: Int
) {
    // HomeViewModel에서 실제 데이터 가져오기
    val homeViewModel: HomeViewModel = hiltViewModel()
    val momProfile by homeViewModel.momProfile.collectAsState()
    val menstrualDate by homeViewModel.menstrualDate.collectAsState()
    val currentPregnancyDay by homeViewModel.currentPregnancyDay.collectAsState()
    val userGender by homeViewModel.userGender.collectAsState()
    val userId by homeViewModel.userId.collectAsState()

    // DiaryViewModel에서 일기 데이터 가져오기
    val diaryViewModel: DiaryViewModel = hiltViewModel()
    val diaryState by diaryViewModel.state.collectAsStateWithLifecycle()

    val backgroundColor = Color(0xFFF5F5F5)
    val title = if (diaryType == "birth") "출산일기" else "관찰일기"

    // 실제 임신 일수와 마지막 생리일 사용
    val actualPregnancyDay = if (day > 0) day else currentPregnancyDay
    val actualMenstrualDate = menstrualDate ?: "2025-01-18"

    // 현재 날짜 계산 (마지막 생리일 + day)
    val currentDate = try {
        val lmpDate = LocalDate.parse(actualMenstrualDate)
        lmpDate.plusDays((actualPregnancyDay - 1).toLong())
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (e: Exception) {
        LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    // HomeViewModel 데이터 초기 로딩
    LaunchedEffect(Unit) {
        Log.d("DiaryBoardScreen", "HomeViewModel 데이터 로드 시작")
        homeViewModel.refreshProfile()
    }

    // HomeViewModel의 데이터를 DiaryViewModel에 전달
    LaunchedEffect(menstrualDate) {
        val currentMenstrualDate = menstrualDate
        if (currentMenstrualDate != null) {
            Log.d("DiaryBoardScreen", "DiaryViewModel에 menstrualDate 설정: $currentMenstrualDate")
            diaryViewModel.setLmpDate(currentMenstrualDate)
        }
    }

    LaunchedEffect(userId, userGender) {
        Log.d("DiaryBoardScreen", "DiaryViewModel에 사용자 정보 설정: userId=$userId, userGender=$userGender")
        diaryViewModel.setUserInfo(userId, userGender)
    }

    // 일기 데이터 로드
    LaunchedEffect(actualPregnancyDay, menstrualDate) {
        val currentMenstrualDate = menstrualDate
        Log.d("DiaryBoardScreen", "일기 데이터 로드 시도:")
        Log.d("DiaryBoardScreen", "  - diaryType: $diaryType")
        Log.d("DiaryBoardScreen", "  - day: $day")
        Log.d("DiaryBoardScreen", "  - actualPregnancyDay: $actualPregnancyDay")
        Log.d("DiaryBoardScreen", "  - currentMenstrualDate: $currentMenstrualDate")

        if (actualPregnancyDay > 0 && currentMenstrualDate != null) {
            Log.d("DiaryBoardScreen", "API 호출 시작: loadDiariesByDay($actualPregnancyDay, $currentMenstrualDate)")
            diaryViewModel.loadDiariesByDay(actualPregnancyDay, currentMenstrualDate)
        } else {
            Log.d("DiaryBoardScreen", "API 호출 조건 미충족 - 대기 중")
        }
    }

    // API에서 로드된 일기 데이터 중 현재 타입에 맞는 일기 찾기
    val currentDiary = diaryState.diaries.find { diary ->
        val expectedRole = if (diaryType == "birth") "FEMALE" else "MALE"
        diary.inferAuthorRole(userId, userGender) == expectedRole
    }

    // 일기 데이터 확인 로그
    LaunchedEffect(diaryState.diaries) {
        Log.d("DiaryBoardScreen", "API 응답 데이터 확인:")
        Log.d("DiaryBoardScreen", "  - 전체 일기 수: ${diaryState.diaries.size}")
        Log.d("DiaryBoardScreen", "  - userId: $userId, userGender: $userGender")

        diaryState.diaries.forEachIndexed { index, diary ->
            val inferredRole = diary.inferAuthorRole(userId, userGender)
            Log.d("DiaryBoardScreen", "  [$index] ID=${diary.diaryId}, 제목='${diary.diaryTitle}', authorRole=${diary.authorRole}, inferredRole=$inferredRole")
        }

        val expectedRole = if (diaryType == "birth") "FEMALE" else "MALE"
        Log.d("DiaryBoardScreen", "  - 찾는 역할: $expectedRole")
        Log.d("DiaryBoardScreen", "  - 찾은 일기: ${if (currentDiary != null) "있음(${currentDiary.diaryTitle})" else "없음"}")
    }

    // DiaryBoardData로 변환 (API 데이터가 없으면 더미 데이터 사용)
    val diaryData = if (currentDiary != null) {
        DiaryBoardData(
            title = currentDiary.diaryTitle ?: "",
            content = currentDiary.diaryContent ?: "",
            photos = emptyList(), // 현재 API에서 사진 데이터는 제공하지 않음
            date = currentDiary.targetDate,
            diaryType = diaryType
        )
    } else {
        // 일기가 없을 때는 빈 데이터
        DiaryBoardData(
            title = "",
            content = "",
            photos = emptyList(),
            date = currentDate,
            diaryType = diaryType
        )
    }

    // HomeViewModel 데이터 로딩 대기
    if (userId == null || userGender == null || menstrualDate == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "사용자 정보를 불러오는 중...",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
        return
    }

    // 로딩 상태 처리
    if (diaryState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "일기를 불러오는 중...",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
        return
    }

    // 에러 상태 처리
    diaryState.errorMessage?.let { error ->
        LaunchedEffect(error) {
            // 에러가 발생하면 로그 출력하고 에러 클리어
            println("DiaryBoardScreen 에러: $error")
            diaryViewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
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
                actions = {
                    // 일기가 존재할 때만 수정 버튼 표시
                    if (currentDiary != null) {
                        IconButton(onClick = {
                            // 최우선으로 출력되는 로그
                            Log.e("DiaryBoardScreen", "🚨🚨🚨 EDIT BUTTON CLICKED!!! 🚨🚨🚨")

                            Log.d("DiaryBoardScreen", "수정 버튼 클릭 - 편집할 일기 설정")
                            Log.d("DiaryBoardScreen", "currentDiary: ${currentDiary}")
                            Log.d("DiaryBoardScreen", "currentDiary.diaryId: ${currentDiary.diaryId}")
                            Log.d("DiaryBoardScreen", "diaryType: $diaryType, day: $day")

                            // 편집할 일기를 DiaryViewModel에 설정
                            diaryViewModel.setEditingDiary(currentDiary)

                            val route = "diary_register/$diaryType/$day/true?diaryId=${currentDiary.diaryId}"
                            Log.d("DiaryBoardScreen", "네비게이션 호출: $route")

                            try {
                                // 일기 수정 화면으로 이동
                                navController.navigate(route)
                                Log.d("DiaryBoardScreen", "네비게이션 성공")
                            } catch (e: Exception) {
                                Log.e("DiaryBoardScreen", "네비게이션 실패: ${e.message}", e)
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "수정"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black,
                    actionIconContentColor = Color.Black
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 사진 섹션
            if (diaryData.photos.isNotEmpty()) {
                item {
                    PhotoSection(
                        photos = diaryData.photos,
                        onCharacterGenerateClick = {
                            // 캐리커쳐 생성 화면으로 이동
                            navController.navigate("character_generate")
                        }
                    )
                }
            }

            // 텍스트 내용 섹션
            item {
                if (diaryData.title.isEmpty() && diaryData.content.isEmpty()) {
                    EmptyDiaryContentSection(
                        diaryType = diaryType,
                        onCreateClick = {
                            // 일기 작성 화면으로 이동
                            navController.navigate("diary_register/$diaryType/$day/false")
                        }
                    )
                } else {
                    TextContentSection(
                        title = diaryData.title,
                        content = diaryData.content
                    )
                }
            }

            // 하단 여백
            item {
                Spacer(modifier = Modifier.height(50.dp))
            }
        }
    }
}

@Composable
fun PhotoSection(
    photos: List<DiaryPhoto>,
    onCharacterGenerateClick: () -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(photos) { photo ->
            PhotoItem(
                photo = photo,
                onCharacterGenerateClick = onCharacterGenerateClick
            )
        }
    }
}

@Composable
fun PhotoItem(
    photo: DiaryPhoto,
    onCharacterGenerateClick: () -> Unit
) {
    Column(
        modifier = Modifier.width(280.dp)
    ) {
        // 사진
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // 실제 앱에서는 Coil이나 Glide를 사용해서 URL로 이미지 로드
                // 여기서는 샘플 이미지 표시
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFE0E0E0)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (photo.type == PhotoType.ULTRASOUND) "초음파 사진" else "일반 사진",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                // 초음파 사진인 경우 캐리커쳐 생성 버튼 표시
                if (photo.type == PhotoType.ULTRASOUND) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Button(
                            onClick = onCharacterGenerateClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF49699)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "캐리커쳐 생성",
                                fontSize = 12.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TextContentSection(
    title: String,
    content: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // 제목
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // 내용
            Text(
                text = content,
                fontSize = 14.sp,
                color = Color.Black,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
fun EmptyDiaryContentSection(
    diaryType: String,
    onCreateClick: () -> Unit
) {
    val diaryTypeName = if (diaryType == "birth") "출산일기" else "관찰일기"
    val borderColor = if (diaryType == "birth") Color(0xFFF49699) else Color(0xFF88A9F8)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "아직 ${diaryTypeName}가 작성되지 않았어요",
                fontSize = 16.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onCreateClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = borderColor
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    text = "${diaryTypeName} 작성하기",
                    fontSize = 14.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// 실제 이미지 로딩을 위한 Composable (Coil 사용 예시)
@Composable
fun NetworkImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    // 실제 구현에서는 Coil의 AsyncImage를 사용
    // AsyncImage(
    //     model = url,
    //     contentDescription = contentDescription,
    //     modifier = modifier,
    //     contentScale = contentScale,
    //     placeholder = painterResource(R.drawable.placeholder),
    //     error = painterResource(R.drawable.error_image)
    // )

    // 현재는 플레이스홀더만 표시
    Box(
        modifier = modifier.background(Color(0xFFE0E0E0)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "이미지 로딩 중...",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DiaryBoardScreenPreview() {
    DiaryBoardScreen(
        navController = null as NavHostController,
        diaryType = "birth",
        day = 1
    )
}