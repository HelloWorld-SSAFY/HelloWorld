package com.ms.helloworld.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCalendarEventDialog(
    selectedDate: String,
    initialTitle: String = "",
    initialContent: String = "",
    initialStartTime: String = "09:00",
    initialEndTime: String = "10:00",
    initialIsRemind: Boolean = false,
    initialOrderNo: Int = 1,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, startTime: String, endTime: String, isRemind: Boolean, orderNo: Int) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var content by remember { mutableStateOf(initialContent) }
    var startTime by remember { mutableStateOf(initialStartTime) }
    var endTime by remember { mutableStateOf(initialEndTime) }
    var isRemind by remember { mutableStateOf(initialIsRemind) }
    var offsetX by remember { mutableStateOf(0f) }

    // TimePicker 상태
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }


    // 등장 애니메이션
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Dialog(onDismissRequest = onDismiss) {
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(animationSpec = tween(300)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeOut(animationSpec = tween(200))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 헤더
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatDateForDisplay(selectedDate)} 일정 작성",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "닫기",
                                tint = Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 제목 입력
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("일정 제목", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Black,
                            focusedLabelColor = Color.Black
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 시간 설정
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 시작 시간
                        OutlinedTextField(
                            value = startTime,
                            onValueChange = { },
                            label = { Text("시작 시간", fontSize = 14.sp) },
                            placeholder = { Text("09:00", fontSize = 14.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    showStartTimePicker = true
                                },
                            singleLine = true,
                            readOnly = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Black,
                                focusedLabelColor = Color.Black,
                                unfocusedBorderColor = Color.Gray,
                                unfocusedLabelColor = Color.Gray,
                                disabledTextColor = Color.Black
                            )
                        )

                        // 종료 시간
                        OutlinedTextField(
                            value = endTime,
                            onValueChange = { },
                            label = { Text("종료 시간", fontSize = 14.sp) },
                            placeholder = { Text("10:00", fontSize = 14.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    showEndTimePicker = true
                                },
                            singleLine = true,
                            readOnly = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Black,
                                focusedLabelColor = Color.Black,
                                unfocusedBorderColor = Color.Gray,
                                unfocusedLabelColor = Color.Gray,
                                disabledTextColor = Color.Black
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 메모 입력
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("메모", fontSize = 14.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Black,
                            focusedLabelColor = Color.Black
                        )
                    )


                    Spacer(modifier = Modifier.height(16.dp))

                    // 리마인드 설정
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "리마인드 알림",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Switch(
                            checked = isRemind,
                            onCheckedChange = { isRemind = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color.Black,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color.Gray
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 버튼
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.Gray
                            )
                        ) {
                            Text("취소", fontSize = 14.sp)
                        }

                        Button(
                            onClick = {
                                if (title.isNotEmpty()) {
                                    onSave(
                                        title,
                                        content,
                                        startTime,
                                        endTime,
                                        isRemind,
                                        initialOrderNo
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = title.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black,
                                contentColor = Color.White
                            )
                        ) {
                            Text("저장", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    // 시작 시간 커스텀 TimePicker Dialog
    if (showStartTimePicker) {
        CustomTimePickerDialog(
            initialTime = startTime,
            onDismiss = { showStartTimePicker = false },
            onTimeSelected = { selectedTime ->
                startTime = selectedTime
                showStartTimePicker = false
            }
        )
    }

    // 종료 시간 커스텀 TimePicker Dialog
    if (showEndTimePicker) {
        CustomTimePickerDialog(
            initialTime = endTime,
            onDismiss = { showEndTimePicker = false },
            onTimeSelected = { selectedTime ->
                endTime = selectedTime
                showEndTimePicker = false
            }
        )
    }
}

private fun formatDateForDisplay(dateKey: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("M월 d일", Locale.getDefault())
        val date = inputFormat.parse(dateKey)
        outputFormat.format(date ?: Date())
    } catch (e: Exception) {
        dateKey
    }
}