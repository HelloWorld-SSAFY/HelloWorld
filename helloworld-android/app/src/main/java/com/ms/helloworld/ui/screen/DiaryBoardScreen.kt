package com.ms.helloworld.ui.screen

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.navigation.NavHostController
import com.ms.helloworld.viewmodel.HomeViewModel
import com.ms.helloworld.viewmodel.DiaryViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
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
        val calculatedDate = lmpDate.plusDays((actualPregnancyDay - 1).toLong())
        Log.d("DiaryBoardScreen", "날짜 계산: LMP=$actualMenstrualDate, 임신일수=$actualPregnancyDay, 계산결과=$calculatedDate")
        calculatedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
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

    // 일기 데이터 로드 - 먼저 일별 조회로 일기 목록을 가져온 다음 개별 조회
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
        val actualRole = diary.inferAuthorRole(userId, userGender)
        actualRole?.uppercase() == expectedRole
    }

    // 일기를 찾았지만 images가 비어있다면 개별 조회로 상세 데이터 가져오기
    LaunchedEffect(currentDiary) {
        if (currentDiary != null && (currentDiary.images == null || currentDiary.images.isEmpty())) {
            Log.d("DiaryBoardScreen", "일기를 찾았지만 images가 비어있음. 개별 조회 시작: diaryId=${currentDiary.diaryId}")
            diaryViewModel.loadDiary(currentDiary.diaryId)
        }
    }

    // 이미지 데이터 로그 출력
    LaunchedEffect(currentDiary) {
        if (currentDiary != null) {
            Log.d("DiaryBoardScreen", "📸 이미지 데이터 확인:")
            Log.d("DiaryBoardScreen", "  - thumbnailUrl: ${currentDiary.thumbnailUrl}")
            val images = currentDiary.images
            if (images != null) {
                Log.d("DiaryBoardScreen", "  - images 배열 크기: ${images.size}")
                images.forEachIndexed { index, image ->
                    Log.d("DiaryBoardScreen", "  - images[$index]: ${image.imageUrl} (초음파: ${image.isUltrasound})")
                }
            } else {
                Log.d("DiaryBoardScreen", "  - images 배열이 null입니다")
            }
        } else {
            Log.d("DiaryBoardScreen", "📸 currentDiary가 null입니다")
        }
    }

    // 일기 데이터 확인 로그
    LaunchedEffect(diaryState.diaries) {
        Log.d("DiaryBoardScreen", "API 응답 데이터 확인:")
        Log.d("DiaryBoardScreen", "  - 전체 일기 수: ${diaryState.diaries.size}")
        Log.d("DiaryBoardScreen", "  - userId: $userId, userGender: $userGender")

        diaryState.diaries.forEachIndexed { index, diary ->
            val inferredRole = diary.inferAuthorRole(userId, userGender)
            val actualTitle = diary.getActualTitle()
            val actualContent = diary.getActualContent()
            Log.d("DiaryBoardScreen", "  [$index] ID=${diary.diaryId}")
            Log.d("DiaryBoardScreen", "       title 필드: '${diary.diaryTitle}'")
            Log.d("DiaryBoardScreen", "       diaryTitle 필드: '${diary.diaryTitleAlt}'")
            Log.d("DiaryBoardScreen", "       실제 제목: '${actualTitle ?: "(제목 없음)"}'")
            Log.d("DiaryBoardScreen", "       content 필드: '${diary.diaryContent?.take(50)}'")
            Log.d("DiaryBoardScreen", "       diaryContent 필드: '${diary.diaryContentAlt?.take(50)}'")
            Log.d("DiaryBoardScreen", "       실제 내용: '${actualContent?.take(50) ?: "(내용 없음)"}'")
            Log.d("DiaryBoardScreen", "       authorRole=${diary.authorRole}, inferredRole=$inferredRole")
            Log.d("DiaryBoardScreen", "  [$index] 이미지 데이터: thumbnailUrl=${diary.thumbnailUrl}, images size=${diary.images?.size ?: "null"}")
        }

        val expectedRole = if (diaryType == "birth") "FEMALE" else "MALE"
        Log.d("DiaryBoardScreen", "  - 찾는 역할: $expectedRole")
        Log.d("DiaryBoardScreen", "  - 찾은 일기: ${if (currentDiary != null) "있음(${currentDiary.getActualTitle() ?: "(제목 없음)"})" else "없음"}")
    }

    // DiaryBoardData로 변환 (API 데이터가 없으면 더미 데이터 사용)
    val diaryData = if (currentDiary != null) {
        val defaultTitle = if (diaryType == "birth") "출산일기" else "관찰일기"
        val actualTitle = currentDiary.getActualTitle()
        val actualContent = currentDiary.getActualContent()
        DiaryBoardData(
            title = actualTitle?.takeIf { it.isNotBlank() } ?: defaultTitle,
            content = actualContent ?: "",
            photos = emptyList(), // 현재 API에서 사진 데이터는 제공하지 않음
            date = currentDiary.targetDate ?: currentDate,
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
                        content = diaryData.content,
                        imageUrl = currentDiary?.thumbnailUrl,
                        images = currentDiary?.images,
                        diaryViewModel = diaryViewModel
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
    content: String,
    imageUrl: String? = null,
    images: List<com.ms.helloworld.dto.response.DiaryImage>? = null,
    diaryViewModel: DiaryViewModel
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
            // 사진 공간 - images 배열의 이미지들 표시
            if (images != null && images.isNotEmpty()) {
                // 초음파 이미지들만 필터링하여 인덱스 매핑용으로 저장
                val ultrasoundImages = remember(images) {
                    images.filter { it.isUltrasound }
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(images) { image ->
                        // 초음파 이미지의 인덱스 계산 (캐리커쳐 API용)
                        val ultrasoundIndex = if (image.isUltrasound) {
                            ultrasoundImages.indexOf(image)
                        } else -1

                        Log.d("DiaryBoardScreen", "Image 데이터: imageUrl=${image.imageUrl}, isUltrasound=${image.isUltrasound}, diaryPhotoId=${image.diaryPhotoId}, ultrasoundIndex=$ultrasoundIndex")

                        FlippableImageCard(
                            imageUrl = image.imageUrl,
                            isUltrasound = image.isUltrasound,
                            diaryPhotoId = image.diaryPhotoId,
                            ultrasoundIndex = ultrasoundIndex,
                            diaryViewModel = diaryViewModel
                        )
                    }
                }
            } else if (!imageUrl.isNullOrEmpty()) {
                // 백업용: 단일 imageUrl이 있는 경우
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    FlippableImageCard(
                        imageUrl = imageUrl,
                        isUltrasound = false,
                        diaryPhotoId = null, // 단일 이미지의 경우 diaryPhotoId가 없을 수 있음
                        ultrasoundIndex = -1, // 단일 이미지는 초음파가 아니므로 -1
                        diaryViewModel = diaryViewModel
                    )
                }
            } else {
                // placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(
                            Color.Gray.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📷 사진 영역",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
fun FlippableImageCard(
    imageUrl: String,
    isUltrasound: Boolean,
    diaryPhotoId: Long?,
    ultrasoundIndex: Int,
    diaryViewModel: DiaryViewModel
) {
    Log.d("FlippableImageCard", "카드 생성: imageUrl=$imageUrl, isUltrasound=$isUltrasound, diaryPhotoId=$diaryPhotoId, ultrasoundIndex=$ultrasoundIndex")

    var flipped by remember { mutableStateOf(false) }
    var caricatureUrl by remember { mutableStateOf<String?>(null) }
    var isLoadingCaricature by remember { mutableStateOf(false) }
    var isGeneratingCaricature by remember { mutableStateOf(false) }

    // 카드가 뒤집힐 때 캐리커쳐 조회
    LaunchedEffect(key1 = "${flipped}_${diaryPhotoId ?: 0L}") {
        Log.d("FlippableImageCard", "LaunchedEffect 실행: flipped=$flipped, isUltrasound=$isUltrasound, diaryPhotoId=$diaryPhotoId, caricatureUrl=$caricatureUrl")

        if (flipped && isUltrasound && caricatureUrl == null) {
            isLoadingCaricature = true
            try {
                Log.d("FlippableImageCard", "캐리커쳐 조회 시작: diaryPhotoId=$diaryPhotoId, ultrasoundIndex=$ultrasoundIndex")

                // diaryPhotoId가 없으면 ultrasoundIndex 기반으로 ID 생성
                val actualPhotoId = diaryPhotoId ?: (ultrasoundIndex + 1).toLong()
                Log.d("FlippableImageCard", "실제 사용할 diaryPhotoId: $actualPhotoId (based on ultrasoundIndex: $ultrasoundIndex)")

                val result = diaryViewModel.getCaricatureFromPhoto(actualPhotoId)
                result.onSuccess { caricature: com.ms.helloworld.dto.response.CaricatureResponse? ->
                    if (caricature != null) {
                        caricatureUrl = caricature.imageUrl
                        Log.d("FlippableImageCard", "캐리커쳐 조회 성공: ${caricature.imageUrl}")
                    } else {
                        Log.d("FlippableImageCard", "캐리커쳐가 없음")
                    }
                }
                result.onFailure { exception ->
                    Log.e("FlippableImageCard", "캐리커쳐 조회 실패: ${exception.message}")
                }
            } catch (e: Exception) {
                Log.e("FlippableImageCard", "캐리커쳐 조회 예외: ${e.message}")
            } finally {
                isLoadingCaricature = false
            }
        }
    }

    val flip by animateFloatAsState(
        targetValue = if (flipped && isUltrasound) 180f else 0f,
        animationSpec = tween(600),
        label = "flip"
    )
    val cameraDistancePx = with(LocalDensity.current) { 12.dp.toPx() }

    Card(
        modifier = Modifier
            .width(320.dp)
            .height(240.dp)
            .clickable {
                if (isUltrasound) {
                    flipped = !flipped
                }
            }
            .graphicsLayer {
                rotationY = if (isUltrasound) flip else 0f
                cameraDistance = cameraDistancePx
            },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Gray.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            if (flipped && isUltrasound) {
                // 카드 뒷면 - 캐리커쳐 영역 (텍스트가 정상적으로 보이도록 다시 180도 회전)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationY = 180f // 뒷면 내용을 다시 180도 회전시켜 정상 방향으로
                        }
                ) {
                    CaricatureBackSide(
                        caricatureUrl = caricatureUrl,
                        isLoading = isLoadingCaricature,
                        isGenerating = isGeneratingCaricature,
                        onGenerateClick = {
                            Log.d("FlippableImageCard", "캐리커쳐 생성 버튼 클릭됨! diaryPhotoId=$diaryPhotoId, isGenerating=$isGeneratingCaricature")
                            if (!isGeneratingCaricature) {
                                isGeneratingCaricature = true
                                Log.d("FlippableImageCard", "캐리커쳐 생성 시작 준비: diaryPhotoId=$diaryPhotoId")

                                // 캐리커쳐 생성 API 호출을 Coroutine으로 실행
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                    try {
                                        Log.d("FlippableImageCard", "캐리커쳐 생성 시작: diaryPhotoId=$diaryPhotoId, ultrasoundIndex=$ultrasoundIndex")

                                        // diaryPhotoId가 없으면 ultrasoundIndex 기반으로 ID 생성
                                        val actualPhotoId = diaryPhotoId ?: (ultrasoundIndex + 1).toLong()
                                        Log.d("FlippableImageCard", "실제 사용할 diaryPhotoId: $actualPhotoId (based on ultrasoundIndex: $ultrasoundIndex)")

                                        val result = diaryViewModel.generateCaricature(actualPhotoId)
                                        result.onSuccess { caricature: com.ms.helloworld.dto.response.CaricatureResponse ->
                                            caricatureUrl = caricature.imageUrl
                                            Log.d("FlippableImageCard", "캐리커쳐 생성 성공: ${caricature.imageUrl}")
                                        }
                                        result.onFailure { exception ->
                                            Log.e("FlippableImageCard", "캐리커쳐 생성 실패: ${exception.message}")
                                        }
                                    } catch (e: Exception) {
                                        Log.e("FlippableImageCard", "캐리커쳐 생성 예외: ${e.message}")
                                    } finally {
                                        isGeneratingCaricature = false
                                    }
                                }
                            }
                        }
                    )
                }
            } else {
                // 카드 앞면 - 원본 이미지
                AsyncImage(
                    model = imageUrl,
                    contentDescription = if (isUltrasound) "초음파 사진" else "일기 사진",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentScale = ContentScale.Fit
                )

                // 초음파 사진인 경우 배지 표시
                if (isUltrasound) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF49699).copy(alpha = 0.9f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "초음파",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CaricatureBackSide(
    caricatureUrl: String?,
    isLoading: Boolean,
    isGenerating: Boolean,
    onGenerateClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                // 캐리커쳐 조회 중
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = Color(0xFFF49699)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "캐리커쳐 확인 중...",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }

            isGenerating -> {
                // 캐리커쳐 생성 중
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = Color(0xFFF49699)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "캐리커쳐 생성 중...",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "잠시만 기다려주세요",
                        fontSize = 12.sp,
                        color = Color.Gray.copy(alpha = 0.7f)
                    )
                }
            }

            caricatureUrl != null -> {
                // 캐리커쳐가 있는 경우
                AsyncImage(
                    model = caricatureUrl,
                    contentDescription = "아기 캐리커쳐",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentScale = ContentScale.Fit
                )

                // 캐리커쳐 배지
                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF88A9F8).copy(alpha = 0.9f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "캐리커쳐",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }

            else -> {
                // 캐리커쳐가 없는 경우 - 생성 버튼 표시
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = "캐리커쳐",
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray.copy(alpha = 0.5f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "아직 캐리커쳐가 없어요",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onGenerateClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF49699)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "캐리커쳐 생성",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }
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