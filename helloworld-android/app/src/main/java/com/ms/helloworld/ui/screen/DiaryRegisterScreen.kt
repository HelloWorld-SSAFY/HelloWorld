package com.ms.helloworld.ui.screen

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.navigation.NavHostController
import com.ms.helloworld.ui.components.CustomTopAppBar
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ms.helloworld.viewmodel.DiaryViewModel
import com.ms.helloworld.viewmodel.HomeViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

// 일기 타입 enum
enum class DiaryType(
    val displayName: String,
    val cardColor: Color,
    val borderColor: Color
) {
    BIRTH("출산일기", Color(0xFFFFEAE7), Color(0xFFF49699)),
    OBSERVATION("관찰일기", Color(0xFFF0F5FF), Color(0xFF88A9F8))
}

@SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryRegisterScreen(
    navController: NavHostController,
    diaryType: String, // "birth" 또는 "observation"
    day: Int,
    isEdit: Boolean = false,
    diaryId: Long? = null
) {
    val backgroundColor = Color(0xFFF5F5F5)
    val currentDiaryType = if (diaryType == "birth") DiaryType.BIRTH else DiaryType.OBSERVATION

    // ViewModels - Activity 레벨에서 동일한 인스턴스 사용
    val diaryViewModel: DiaryViewModel = hiltViewModel()
    val homeViewModel: HomeViewModel = hiltViewModel()
    val diaryState by diaryViewModel.state.collectAsStateWithLifecycle()
    val momProfile by homeViewModel.momProfile.collectAsState()
    val userGender by homeViewModel.userGender.collectAsState()
    val userId by homeViewModel.userId.collectAsState()
    val coupleId by homeViewModel.coupleId.collectAsState()
    val menstrualDate by homeViewModel.menstrualDate.collectAsState()
    val currentPregnancyDay by homeViewModel.currentPregnancyDay.collectAsState()

    // coupleId는 서버에서 토큰으로 자동 처리됨
    val getLmpDate = {
        menstrualDate ?: "2025-01-18" // HomeViewModel과 동일한 기본값 사용
    }

    // 날짜 계산 (임신 일수 -> 실제 날짜) - 네겔레 법칙 사용
    val targetDate = remember(day) {
        val lmpDateString = getLmpDate()
        val lmpDate = LocalDate.parse(lmpDateString)

        // 수정된 계산: 마지막 생리일 + day일 (day일차는 LMP + day일)
        val actualDate = lmpDate.plusDays(day.toLong())
        actualDate.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"))
    }

    val targetDateForApi = remember(day) {
        val lmpDateString = getLmpDate()
        val lmpDate = LocalDate.parse(lmpDateString)

        // 수정된 계산: 마지막 생리일 + day일 (day일차는 LMP + day일)
        val actualDate = lmpDate.plusDays(day.toLong())
        val result = actualDate.toString() // yyyy-MM-dd 형식

        Log.d("DiaryRegisterScreen", "targetDate 계산: day=$day, lmp=$lmpDateString, result=$result")

        result
    }

    // 편집할 일기 데이터 가져오기
    val editingDiary = diaryState.editingDiary

    // diaryId가 있으면 해당 일기를 editingDiary로 설정
    LaunchedEffect(diaryId, isEdit) {
        Log.d("DiaryRegisterScreen", "첫 번째 LaunchedEffect 실행:")
        Log.d("DiaryRegisterScreen", "  - isEdit: $isEdit")
        Log.d("DiaryRegisterScreen", "  - diaryId: $diaryId")
        Log.d("DiaryRegisterScreen", "  - diaryId != null: ${diaryId != null}")
        Log.d("DiaryRegisterScreen", "  - diaryId != -1L: ${diaryId != -1L}")
        Log.d("DiaryRegisterScreen", "  - editingDiary == null: ${editingDiary == null}")

        if (isEdit && diaryId != null && diaryId != -1L && editingDiary == null) {
            Log.d("DiaryRegisterScreen", "diaryId로 편집할 일기 찾는 중: diaryId=$diaryId")
            // 먼저 일기 데이터를 로드
            diaryViewModel.loadDiariesByDay(day, getLmpDate())
        }
    }

    // 일기 데이터가 로드된 후 diaryId로 편집할 일기 찾기
    LaunchedEffect(diaryState.diaries, diaryId, isEdit) {
        Log.d("DiaryRegisterScreen", "두 번째 LaunchedEffect 실행:")
        Log.d("DiaryRegisterScreen", "  - isEdit: $isEdit")
        Log.d("DiaryRegisterScreen", "  - diaryId: $diaryId")
        Log.d("DiaryRegisterScreen", "  - diaryState.diaries.size: ${diaryState.diaries.size}")
        Log.d("DiaryRegisterScreen", "  - editingDiary == null: ${editingDiary == null}")

        if (isEdit && diaryId != null && diaryId != -1L && editingDiary == null && diaryState.diaries.isNotEmpty()) {
            Log.d("DiaryRegisterScreen", "로드된 일기에서 diaryId=$diaryId 찾는 중 (총 ${diaryState.diaries.size}개)")
            val targetDiary = diaryState.diaries.find { it.diaryId == diaryId }
            if (targetDiary != null) {
                Log.d("DiaryRegisterScreen", "편집할 일기 발견: ${targetDiary.diaryTitle}")
                diaryViewModel.setEditingDiary(targetDiary)
            } else {
                Log.w("DiaryRegisterScreen", "diaryId=${diaryId}에 해당하는 일기를 찾을 수 없습니다. 로드된 일기: ${diaryState.diaries.map { it.diaryId }}")
            }
        }
    }

    // 입력 상태들
    var diaryTitle by remember { mutableStateOf("") }
    var diaryContent by remember { mutableStateOf("") }
    var selectedPhotos by remember { mutableStateOf<List<Uri>>(emptyList()) } // Uri로 변경
    var selectedUltrasoundPhotos by remember { mutableStateOf<List<Int>>(emptyList()) } // idx 값으로 변경
    var ultrasounds by remember { mutableStateOf<List<Boolean>>(emptyList()) } // 초음파 사진 여부 리스트

    // 로딩 상태 관리
    var isSubmitting by remember { mutableStateOf(false) }

    // 이미지 선택 런처 (일반 사진)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val newPhotoIndex = selectedPhotos.size
            selectedPhotos = selectedPhotos + it

            // ultrasounds 리스트 업데이트 (새로 추가된 사진을 일반 사진으로 표시)
            ultrasounds = ultrasounds.toMutableList().apply {
                while (size <= newPhotoIndex) {
                    add(false) // 일반 사진은 false
                }
            }

            Log.d("DiaryRegisterScreen", "일반 사진 선택: uri=$it, idx=$newPhotoIndex")
            Log.d("DiaryRegisterScreen", "ultrasounds list: $ultrasounds")
        }
    }

    // 이미지 선택 런처 (초음파 사진)
    val ultrasoundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val newPhotoIndex = selectedPhotos.size
            selectedPhotos = selectedPhotos + it
            selectedUltrasoundPhotos = selectedUltrasoundPhotos + newPhotoIndex

            // ultrasounds 리스트 업데이트 (새로 추가된 사진을 초음파로 표시)
            ultrasounds = ultrasounds.toMutableList().apply {
                while (size <= newPhotoIndex) {
                    add(false) // 기본값은 false
                }
                if (newPhotoIndex < size) {
                    set(newPhotoIndex, true) // 새로 추가된 사진은 초음파
                }
            }

            Log.d("DiaryRegisterScreen", "초음파 사진 선택: uri=$it, idx=$newPhotoIndex")
            Log.d("DiaryRegisterScreen", "ultrasounds list: $ultrasounds")
        }
    }

    // 편집 모드에서 데이터 로딩 시 입력 필드 업데이트
    LaunchedEffect(editingDiary, isEdit) {
        Log.d("DiaryRegisterScreen", "LaunchedEffect 실행: isEdit=$isEdit, editingDiary=$editingDiary")
        if (isEdit && editingDiary != null) {
            val newTitle = editingDiary.diaryTitle ?: ""
            val newContent = editingDiary.diaryContent ?: ""
            Log.d("DiaryRegisterScreen", "편집 데이터 로딩 시도: ID=${editingDiary.diaryId}, 제목='$newTitle', 내용 길이=${newContent.length}")

            diaryTitle = newTitle
            diaryContent = newContent

            Log.d("DiaryRegisterScreen", "편집 데이터 로딩 완료: 제목='$diaryTitle', 내용 길이=${diaryContent.length}")
        } else if (!isEdit) {
            // 새로 작성하는 경우 초기화
            diaryTitle = ""
            diaryContent = ""
            Log.d("DiaryRegisterScreen", "새 일기 작성 모드로 초기화")
        } else if (isEdit && editingDiary == null) {
            Log.w("DiaryRegisterScreen", "편집 모드인데 editingDiary가 null입니다!")
        }
    }

    // 등록/수정 완료 후 화면 이동
    LaunchedEffect(diaryState.isLoading) {
        if (!diaryState.isLoading && isSubmitting) {
            if (diaryState.errorMessage == null) {
                Log.d("DiaryRegisterScreen", "일기 등록/수정 성공, 화면 이동")

                // 편집 모드였다면 편집 데이터 클리어
                if (isEdit) {
                    diaryViewModel.clearEditingDiary()
                }

                navController.popBackStack()
            }
            isSubmitting = false
        }
    }

    // 화면을 벗어날 때 편집 데이터 클리어
    DisposableEffect(Unit) {
        onDispose {
            if (isEdit) {
                diaryViewModel.clearEditingDiary()
                Log.d("DiaryRegisterScreen", "화면 종료 시 편집 데이터 클리어")
            }
        }
    }

    // HomeViewModel의 실제 데이터를 DiaryViewModel에 전달
    LaunchedEffect(coupleId, menstrualDate) {
        val actualCoupleId = coupleId
        val actualMenstrualDate = menstrualDate
        if (actualCoupleId != null && actualMenstrualDate != null) {
            Log.d("DiaryRegisterScreen", "DiaryViewModel에 실제 데이터 전달: coupleId=$actualCoupleId, menstrualDate=$actualMenstrualDate")
        }
    }

    // 에러 메시지 자동 클리어
    LaunchedEffect(diaryState.errorMessage) {
        if (diaryState.errorMessage != null) {
            kotlinx.coroutines.delay(5000) // 5초 후 에러 메시지 클리어
            diaryViewModel.clearError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        CustomTopAppBar(
            title = currentDiaryType.displayName,
            navController = navController
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 메인 카드
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 2.dp,
                        color = currentDiaryType.borderColor,
                        shape = RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = currentDiaryType.cardColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 날짜 표시
                    Text(
                        text = "$targetDate (${day}일차)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 제목 입력 필드
                    DiaryTitleInput(
                        value = diaryTitle,
                        onValueChange = { diaryTitle = it },
                        placeholder = "${currentDiaryType.displayName} 제목을 입력해주세요..."
                    )

                    // 사진 등록 버튼
                    PhotoRegisterButton(
                        title = "사진 등록",
                        onClick = {
                            imagePickerLauncher.launch("image/*")
                        }
                    )

                    // 초음파 사진 등록 버튼 (출산일기만)
                    if (currentDiaryType == DiaryType.BIRTH) {
                        PhotoRegisterButton(
                            title = "초음파 사진 등록",
                            onClick = {
                                ultrasoundPickerLauncher.launch("image/*")
                            }
                        )
                    }

                    // 선택된 사진 미리보기
                    if (selectedPhotos.isNotEmpty()) {
                        Text(
                            text = "선택된 사진 (${selectedPhotos.size}장)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            itemsIndexed(selectedPhotos) { index, photoUri ->
                                Card(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clickable {
                                            // 사진 삭제
                                            selectedPhotos = selectedPhotos.filterIndexed { i, _ -> i != index }
                                            selectedUltrasoundPhotos = selectedUltrasoundPhotos.filter { it != index }
                                            ultrasounds = ultrasounds.filterIndexed { i, _ -> i != index }
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.White
                                    ),
                                    border = BorderStroke(
                                        2.dp,
                                        if (ultrasounds.getOrNull(index) == true)
                                            Color(0xFF2196F3) else Color(0xFF9E9E9E)
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        // 실제 이미지 표시
                                        AsyncImage(
                                            model = photoUri,
                                            contentDescription = "선택된 사진",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )

                                        // 타입 표시 오버레이
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .background(
                                                    Color.Black.copy(alpha = 0.7f),
                                                    RoundedCornerShape(topStart = 4.dp)
                                                )
                                                .padding(4.dp)
                                        ) {
                                            Text(
                                                text = if (ultrasounds.getOrNull(index) == true) "🩻" else "📷",
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 텍스트 입력 영역
                    DiaryTextInput(
                        value = diaryContent,
                        onValueChange = { diaryContent = it },
                        placeholder = "${currentDiaryType.displayName}를 작성해주세요..."
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 에러 메시지 표시
            if (diaryState.errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "오류: ${diaryState.errorMessage}",
                        modifier = Modifier.padding(16.dp),
                        color = Color.Red,
                        fontSize = 14.sp
                    )
                }
            }

            // 등록 버튼
            RegisterButton(
                text = if (isEdit) "수정" else "등록",
                enabled = !diaryState.isLoading && diaryTitle.isNotBlank() && diaryContent.isNotBlank(),
                onClick = {
                    if (!isSubmitting) {
                        isSubmitting = true
                        Log.d("DiaryRegisterScreen", "일기 등록 시작")
                        Log.d("DiaryRegisterScreen", "제목: $diaryTitle")
                        Log.d("DiaryRegisterScreen", "내용: $diaryContent")
                        Log.d("DiaryRegisterScreen", "날짜: $targetDateForApi")

                        if (isEdit && editingDiary != null) {
                            // 일기 수정
                            Log.d("DiaryRegisterScreen", "일기 수정 시작: ID=${editingDiary.diaryId}")
                            Log.d("DiaryRegisterScreen", "수정할 일기 정보: 제목='${editingDiary.diaryTitle}', 작성자ID=${editingDiary.authorId}")
                            diaryViewModel.updateDiary(
                                diaryId = editingDiary.diaryId,
                                title = diaryTitle,
                                content = diaryContent,
                                targetDate = targetDateForApi
                            )
                        } else if (isEdit && editingDiary == null) {
                            Log.e("DiaryRegisterScreen", "편집 모드인데 editingDiary가 null입니다. 수정을 진행할 수 없습니다.")
                            isSubmitting = false
                        } else {
                            // 사용자 성별에 따라 authorRole 결정
                            val authorRole = when (userGender?.lowercase()) {
                                "female" -> "FEMALE"
                                "male" -> "MALE"
                                else -> if (diaryType == "birth") "FEMALE" else "MALE" // fallback
                            }

                            Log.d("DiaryRegisterScreen", "authorRole: $authorRole (gender: $userGender)")
                            Log.d("DiaryRegisterScreen", "userId: $userId")
                            Log.d("DiaryRegisterScreen", "coupleId: $coupleId")
                            Log.d("DiaryRegisterScreen", "day: $day")
                            Log.d("DiaryRegisterScreen", "targetDateForApi: $targetDateForApi")
                            Log.d("DiaryRegisterScreen", "lmpDate: ${getLmpDate()}")
                            Log.d("DiaryRegisterScreen", "menstrualDate raw: $menstrualDate")
                            Log.d("DiaryRegisterScreen", "계산 검증: 생리일 + day = ${getLmpDate()} + $day = $targetDateForApi")
                            Log.d("DiaryRegisterScreen", "디버깅 - userGender: $userGender, userId: $userId, coupleId: $coupleId")
                            Log.d("DiaryRegisterScreen", "디버깅 - menstrualDate: $menstrualDate, momProfile: $momProfile")

                            // 초음파 사진 관련 로그
                            Log.d("DiaryRegisterScreen", "선택된 사진 개수: ${selectedPhotos.size}")
                            Log.d("DiaryRegisterScreen", "ultrasounds 리스트: $ultrasounds")
                            selectedPhotos.forEachIndexed { index, photo ->
                                val isUltrasound = ultrasounds.getOrNull(index) ?: false
                                Log.d("DiaryRegisterScreen", "사진[$index]: $photo, 초음파 여부: $isUltrasound")
                            }

                            // 선택된 사진이 있으면 Multipart 업로드, 없으면 기존 방식
                            if (selectedPhotos.isNotEmpty()) {
                                Log.d("DiaryRegisterScreen", "🚀 Multipart 업로드 시작")
                                diaryViewModel.createDiaryWithFiles(
                                    title = diaryTitle,
                                    content = diaryContent,
                                    targetDate = targetDateForApi,
                                    authorRole = authorRole,
                                    authorId = userId ?: 0L,
                                    imageUris = selectedPhotos,
                                    ultrasounds = ultrasounds
                                )
                            } else {
                                Log.d("DiaryRegisterScreen", "📝 기존 방식 업로드 (사진 없음)")
                                diaryViewModel.createDiary(
                                    title = diaryTitle,
                                    content = diaryContent,
                                    targetDate = targetDateForApi,
                                    authorRole = authorRole,
                                    authorId = userId ?: 0L
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun PhotoRegisterButton(
    title: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = null,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = Color.Black
        )
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

@Composable
fun DiaryTitleInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            },
            modifier = Modifier.fillMaxSize(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                color = Color.Black
            ),
            singleLine = true
        )
    }
}

@Composable
fun DiaryTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            },
            modifier = Modifier.fillMaxSize(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                color = Color.Black
            )
        )
    }
}

@Composable
fun RegisterButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFF49699),
            disabledContainerColor = Color.Gray
        )
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

// 네비게이션 헬퍼 함수들
@Composable
fun WriteBirthDiaryScreen(
    navController: NavHostController,
    day: Int
) {
    DiaryRegisterScreen(
        navController = navController,
        diaryType = "birth",
        day = day,
        isEdit = false
    )
}

@Composable
fun EditBirthDiaryScreen(
    navController: NavHostController,
    day: Int,
    diaryId: Long? = null
) {
    DiaryRegisterScreen(
        navController = navController,
        diaryType = "birth",
        day = day,
        isEdit = true,
        diaryId = diaryId
    )
}

@Composable
fun WriteObservationDiaryScreen(
    navController: NavHostController,
    day: Int
) {
    DiaryRegisterScreen(
        navController = navController,
        diaryType = "observation",
        day = day,
        isEdit = false
    )
}

@Composable
fun EditObservationDiaryScreen(
    navController: NavHostController,
    day: Int,
    diaryId: Long? = null
) {
    DiaryRegisterScreen(
        navController = navController,
        diaryType = "observation",
        day = day,
        isEdit = true,
        diaryId = diaryId
    )
}

@Preview(showBackground = true)
@Composable
fun DiaryRegisterScreenPreview() {
    // 출산일기 미리보기
    DiaryRegisterScreen(
        navController = null as NavHostController,
        diaryType = "birth",
        day = 1,
        isEdit = false,
        diaryId = null
    )
}

@Preview(showBackground = true)
@Composable
fun ObservationDiaryRegisterScreenPreview() {
    // 관찰일기 미리보기
    DiaryRegisterScreen(
        navController = null as NavHostController,
        diaryType = "observation",
        day = 1,
        isEdit = false,
        diaryId = null
    )
}