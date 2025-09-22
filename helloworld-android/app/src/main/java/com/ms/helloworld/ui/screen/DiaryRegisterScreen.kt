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
import com.ms.helloworld.ui.components.CustomTopAppBar
import androidx.hilt.navigation.compose.hiltViewModel
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
    isEdit: Boolean = false
) {
    val backgroundColor = Color(0xFFF5F5F5)
    val currentDiaryType = if (diaryType == "birth") DiaryType.BIRTH else DiaryType.OBSERVATION

    // ViewModels
    val diaryViewModel: DiaryViewModel = hiltViewModel()
    val homeViewModel: HomeViewModel = hiltViewModel()
    val diaryState by diaryViewModel.state.collectAsStateWithLifecycle()
    val momProfile by homeViewModel.momProfile.collectAsState()
    val userGender by homeViewModel.userGender.collectAsState()
    val userId by homeViewModel.userId.collectAsState()
    val coupleId by homeViewModel.coupleId.collectAsState()
    val menstrualDate by homeViewModel.menstrualDate.collectAsState()
    val currentPregnancyDay by homeViewModel.currentPregnancyDay.collectAsState()

    val getCoupleId = { coupleId ?: 0L } // coupleId 사용
    val getLmpDate = {
        menstrualDate ?: "2025-05-15" // couple 데이터의 menstrualDate 사용
    }

    // 날짜 계산 (임신 일수 -> 실제 날짜) - 네겔레 법칙 사용
    val targetDate = remember(day) {
        val lmpDateString = getLmpDate()
        val lmpDate = LocalDate.parse(lmpDateString)

        // 네겔레 법칙: 마지막 생리일 + (day-1)일
        val actualDate = lmpDate.plusDays((day - 1).toLong())
        actualDate.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"))
    }

    val targetDateForApi = remember(day) {
        val lmpDateString = getLmpDate()
        val lmpDate = LocalDate.parse(lmpDateString)

        // 네겔레 법칙: 마지막 생리일 + (day-1)일
        val actualDate = lmpDate.plusDays((day - 1).toLong())
        val result = actualDate.toString() // yyyy-MM-dd 형식

        println("📅 targetDate 계산 (네겔레 법칙):")
        println("  - day: $day")
        println("  - lmpDateString: $lmpDateString")
        println("  - lmpDate: $lmpDate")
        println("  - plusDays: ${day - 1}")
        println("  - actualDate: $actualDate")
        println("  - result: $result")

        result
    }

    // 입력 상태들
    var diaryTitle by remember { mutableStateOf("") }
    var diaryContent by remember { mutableStateOf("") }
    var selectedPhotos by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedUltrasoundPhotos by remember { mutableStateOf<List<String>>(emptyList()) }

    // 로딩 상태 관리
    var isSubmitting by remember { mutableStateOf(false) }

    // 등록/수정 완료 후 화면 이동
    LaunchedEffect(diaryState.isLoading) {
        if (!diaryState.isLoading && isSubmitting) {
            if (diaryState.errorMessage == null) {
                println("✅ DiaryRegisterScreen - 일기 등록/수정 성공, 화면 이동")
                navController.popBackStack()
            }
            isSubmitting = false
        }
    }

    // HomeViewModel의 실제 데이터를 DiaryViewModel에 전달
    LaunchedEffect(coupleId, menstrualDate) {
        val actualCoupleId = coupleId
        val actualMenstrualDate = menstrualDate
        if (actualCoupleId != null && actualMenstrualDate != null) {
            println("📝 DiaryRegisterScreen - DiaryViewModel에 실제 데이터 전달: coupleId=$actualCoupleId, menstrualDate=$actualMenstrualDate")
            diaryViewModel.setCoupleInfo(actualCoupleId, actualMenstrualDate)
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
                            // 사진 선택 로직
                        }
                    )

                    // 초음파 사진 등록 버튼 (출산일기만)
                    if (currentDiaryType == DiaryType.BIRTH) {
                        PhotoRegisterButton(
                            title = "초음파 사진 등록",
                            onClick = {
                                // 초음파 사진 선택 로직
                            }
                        )
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
                        println("📝 DiaryRegisterScreen - 일기 등록 시작")
                        println("📝 제목: $diaryTitle")
                        println("📝 내용: $diaryContent")
                        println("📝 날짜: $targetDateForApi")

                        if (isEdit) {
                            // TODO: 수정 로직 (diaryId 필요)
                            println("📝 일기 수정 기능은 추후 구현")
                        } else {
                            // 사용자 성별에 따라 authorRole 결정
                            val authorRole = when (userGender?.lowercase()) {
                                "female" -> "FEMALE"
                                "male" -> "MALE"
                                else -> if (diaryType == "birth") "FEMALE" else "MALE" // fallback
                            }

                            println("📝 DiaryRegisterScreen - authorRole: $authorRole (gender: $userGender)")
                            println("📝 DiaryRegisterScreen - userId: $userId")
                            println("📝 DiaryRegisterScreen - coupleId: $coupleId")
                            println("📝 DiaryRegisterScreen - day: $day")
                            println("📝 DiaryRegisterScreen - targetDateForApi: $targetDateForApi")
                            println("📝 DiaryRegisterScreen - lmpDate: ${getLmpDate()}")
                            println("📝 DiaryRegisterScreen - menstrualDate raw: $menstrualDate")
                            println("📝 DiaryRegisterScreen - 계산 검증:")
                            println("   생리일 + (day-1) = ${getLmpDate()} + ${day-1} = $targetDateForApi")
                            println("📝 디버깅: HomeViewModel 상태 확인")
                            println("   - userGender: $userGender")
                            println("   - userId: $userId (expected: not null)")
                            println("   - coupleId: $coupleId (expected: not 1)")
                            println("   - menstrualDate: $menstrualDate (expected: 2025-05-15)")
                            println("   - momProfile: $momProfile")

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
    day: Int
) {
    DiaryRegisterScreen(
        navController = navController,
        diaryType = "birth",
        day = day,
        isEdit = true
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
    day: Int
) {
    DiaryRegisterScreen(
        navController = navController,
        diaryType = "observation",
        day = day,
        isEdit = true
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
        isEdit = false
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
        isEdit = false
    )
}