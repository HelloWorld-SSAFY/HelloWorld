package com.ms.helloworld.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ms.helloworld.ui.theme.MainColor
import com.ms.helloworld.ui.theme.SubColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCalendarEventBottomSheet(
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
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var content by remember(initialContent) { mutableStateOf(initialContent) }
    var startTime by remember(initialStartTime) { mutableStateOf(initialStartTime) }
    var endTime by remember(initialEndTime) { mutableStateOf(initialEndTime) }
    var isRemind by remember(initialIsRemind) { mutableStateOf(initialIsRemind) }
    var offsetX by remember { mutableStateOf(0f) }

    // TimePicker 상태
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }


    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    // BottomSheet 사용
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(
                        Color.Gray.copy(alpha = 0.4f),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 40.dp)
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
                            focusedBorderColor = MainColor,
                            focusedLabelColor = MainColor
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 시간 설정
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 시작 시간
                        Box(Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = startTime,
                                onValueChange = { }, // 읽기전용
                                label = { Text("시작 시간", fontSize = 14.sp) },
                                placeholder = { Text("09:00", fontSize = 14.sp) },
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MainColor,
                                    focusedLabelColor = MainColor,
                                    unfocusedBorderColor = Color.Gray,
                                    unfocusedLabelColor = Color.Gray,
                                    disabledTextColor = Color.Black
                                )
                            )

                            // 클릭을 확실히 잡는 투명 오버레이
                            Spacer(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) { showStartTimePicker = true }
                            )
                        }

                        // 종료 시간
                        Box(Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = endTime,
                                onValueChange = { },
                                label = { Text("종료 시간", fontSize = 14.sp) },
                                placeholder = { Text("10:00", fontSize = 14.sp) },
                                modifier = Modifier,
                                singleLine = true,
                                readOnly = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MainColor,
                                    focusedLabelColor = MainColor,
                                    unfocusedBorderColor = Color.Gray,
                                    unfocusedLabelColor = Color.Gray,
                                    disabledTextColor = Color.Black
                                )
                            )
                            Spacer(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        ) { showEndTimePicker = true }
                                    )
                        }
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
                            focusedBorderColor = MainColor,
                            focusedLabelColor = MainColor
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
                                checkedTrackColor = MainColor,
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
                                containerColor = MainColor,
                                contentColor = Color.White
                            )
                        ) {
                            Text("저장", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
        } // Column 닫기
    } // BottomSheet 닫기

    // 시작 시간 TimePicker
    if (showStartTimePicker) {
        KoreanTimePicker(
            time = try {
                val parts = startTime.split(":")
                LocalTime.of(parts[0].toInt(), parts[1].toInt())
            } catch (e: Exception) {
                LocalTime.of(9, 0)
            },
            onTimeSelected = { selectedTime ->
                startTime = String.format("%02d:%02d", selectedTime.hour, selectedTime.minute)
                showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false },
            is24Hour = true,
            minuteStep = 1
        )
    }

    // 종료 시간 TimePicker
    if (showEndTimePicker) {
        KoreanTimePicker(
            time = try {
                val parts = endTime.split(":")
                LocalTime.of(parts[0].toInt(), parts[1].toInt())
            } catch (e: Exception) {
                LocalTime.of(10, 0)
            },
            onTimeSelected = { selectedTime ->
                endTime = String.format("%02d:%02d", selectedTime.hour, selectedTime.minute)
                showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false },
            is24Hour = true,
            minuteStep = 1
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