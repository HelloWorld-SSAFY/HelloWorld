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
import java.text.SimpleDateFormat
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

    // 현재 날짜 포맷팅
    val currentDate = remember {
        val formatter = SimpleDateFormat("00년 00월 00일 넘버 (자동생성)", Locale.KOREAN)
        formatter.format(Date())
    }

    // 입력 상태들
    var diaryContent by remember { mutableStateOf("") }
    var selectedPhotos by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedUltrasoundPhotos by remember { mutableStateOf<List<String>>(emptyList()) }

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
                        text = currentDate,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
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

            // 등록 버튼
            RegisterButton(
                text = if (isEdit) "수정" else "등록",
                onClick = {
                    // 등록/수정 로직
                    navController.popBackStack()
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
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFF49699)
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