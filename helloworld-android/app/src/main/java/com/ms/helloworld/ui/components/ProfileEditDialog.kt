package com.ms.helloworld.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

@Composable
fun ProfileEditDialog(
    currentNickname: String,
    currentDueDate: LocalDate?,
    currentAge: Int?,
    currentMenstrualDate: LocalDate?,
    currentGender: String?, // "FEMALE" or "MALE"
    onDismiss: () -> Unit,
    onSave: (nickname: String, age: Int?, menstrualDate: LocalDate?, dueDate: LocalDate?) -> Unit
) {
    var nickname by remember { mutableStateOf(currentNickname) }
    var age by remember { mutableStateOf(currentAge?.toString() ?: "") }
    var menstrualDate by remember { mutableStateOf(currentMenstrualDate ?: LocalDate.now()) }
    var menstrualCycle by remember { mutableStateOf("28") } // 기본 28일 주기
    var showDatePicker by remember { mutableStateOf(false) }

    // 생리일자와 주기로 출산예정일 자동 계산
    val calculatedDueDate = remember(menstrualDate, menstrualCycle) {
        val cycle = menstrualCycle.toIntOrNull() ?: 28
        // 마지막 생리일 + 280일 (40주) = 출산예정일
        menstrualDate.plusDays(280)
    }

    val isFemale = currentGender?.uppercase() == "FEMALE"

    // 성별 디버깅
    println("👩 ProfileEditDialog - currentGender: $currentGender")
    println("👩 ProfileEditDialog - isFemale: $isFemale")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // 헤더 (제목과 닫기 버튼)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "프로필 수정",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "닫기",
                            tint = Color(0xFF666666)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 프로필 사진 섹션
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "프로필 사진",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF333333)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(
                                Color(0xFFA8D5A8),
                                CircleShape
                            )
                            .border(
                                2.dp,
                                Color(0xFF6200EE).copy(alpha = 0.3f),
                                CircleShape
                            )
                            .clickable {
                                // TODO: 이미지 선택 기능 구현
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "사진 변경",
                                modifier = Modifier.size(24.dp),
                                tint = Color(0xFF666666)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "사진 변경",
                                fontSize = 10.sp,
                                color = Color(0xFF666666),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 닉네임 입력
                Column {
                    Text(
                        text = "닉네임",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF333333)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = "닉네임을 입력하세요",
                                color = Color(0xFF999999)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6200EE),
                            unfocusedBorderColor = Color(0xFFE0E0E0)
                        ),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 나이 입력
                Column {
                    Text(
                        text = "나이",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF333333)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = age,
                        onValueChange = { newAge ->
                            // 숫자만 입력 허용
                            if (newAge.all { it.isDigit() } && newAge.length <= 3) {
                                age = newAge
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = "나이를 입력하세요",
                                color = Color(0xFF999999)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6200EE),
                            unfocusedBorderColor = Color(0xFFE0E0E0)
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 여성일 경우에만 생리일자 입력 표시
                if (isFemale) {
                    Column {
                        Text(
                            text = "마지막 생리일자",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF333333)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showDatePicker = true
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = menstrualDate.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일")),
                                    fontSize = 16.sp,
                                    color = Color(0xFF333333)
                                )
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "날짜 선택",
                                    tint = Color(0xFF6200EE),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 생리주기 입력
                    Column {
                        Text(
                            text = "생리주기 (일)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF333333)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = menstrualCycle,
                            onValueChange = { newCycle ->
                                // 숫자만 입력 허용 (1-60일 범위)
                                if (newCycle.all { it.isDigit() } && newCycle.length <= 2) {
                                    val cycleInt = newCycle.toIntOrNull()
                                    if (cycleInt == null || cycleInt in 1..60) {
                                        menstrualCycle = newCycle
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    text = "예: 28일",
                                    color = Color(0xFF999999)
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF6200EE),
                                unfocusedBorderColor = Color(0xFFE0E0E0)
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 저장 및 취소 버튼
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 취소 버튼
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF6200EE)
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF6200EE))
                        )
                    ) {
                        Text(
                            text = "취소",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // 저장 버튼
                    Button(
                        onClick = {
                            if (nickname.isNotBlank()) {
                                val ageInt = age.toIntOrNull()
                                val finalMenstrualDate = if (isFemale) menstrualDate else null
                                val finalDueDate = if (isFemale) calculatedDueDate else null
                                onSave(
                                    nickname.trim(),
                                    ageInt,
                                    finalMenstrualDate,
                                    finalDueDate
                                )
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6200EE),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = nickname.isNotBlank()
                    ) {
                        Text(
                            text = "저장",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 생리일자 선택기
        if (showDatePicker) {
            CustomDatePickerDialog(
                currentDate = menstrualDate,
                title = "마지막 생리일자 선택",
                onDateSelected = { selectedDate ->
                    menstrualDate = selectedDate
                    showDatePicker = false
                },
                onDismiss = { showDatePicker = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDatePickerDialog(
    currentDate: LocalDate,
    title: String = "날짜 선택",
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = currentDate.toEpochDay() * 24 * 60 * 60 * 1000
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = LocalDate.ofEpochDay(millis / (24 * 60 * 60 * 1000))
                        onDateSelected(selectedDate)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6200EE)
                )
            ) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    ) {
        DatePicker(
            state = datePickerState,
            showModeToggle = false
        )
    }
}