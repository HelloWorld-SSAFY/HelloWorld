package com.ms.helloworld.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

@Composable
fun CustomTimePickerDialog(
    initialTime: String = "09:00",
    onDismiss: () -> Unit,
    onTimeSelected: (String) -> Unit
) {
    val (initialHour, initialMinute) = remember {
        val parts = initialTime.split(":")
        val hour = if (parts.size >= 2) parts[0].toIntOrNull() ?: 9 else 9
        val minute = if (parts.size >= 2) parts[1].toIntOrNull() ?: 0 else 0
        Pair(hour, minute)
    }

    var selectedHour by remember { mutableStateOf(initialHour) }
    var selectedMinute by remember { mutableStateOf(initialMinute) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 헤더
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "시간 선택",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
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

                Spacer(modifier = Modifier.height(24.dp))

                // 시간 표시
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${String.format("%02d", selectedHour)}:${String.format("%02d", selectedMinute)}",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 타임 피커 휠
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 시간 선택기
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "시",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TimeWheelPicker(
                            items = (0..23).toList(),
                            selectedIndex = selectedHour,
                            onSelectionChanged = { selectedHour = it },
                            modifier = Modifier.width(80.dp)
                        )
                    }

                    Text(
                        text = ":",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    // 분 선택기
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "분",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TimeWheelPicker(
                            items = (0..59 step 5).toList(), // 5분 단위
                            selectedIndex = selectedMinute,
                            onSelectionChanged = { selectedMinute = it },
                            modifier = Modifier.width(80.dp)
                        )
                    }
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
                            val timeString = "${String.format("%02d", selectedHour)}:${String.format("%02d", selectedMinute)}"
                            onTimeSelected(timeString)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black,
                            contentColor = Color.White
                        )
                    ) {
                        Text("확인", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun TimeWheelPicker(
    items: List<Int>,
    selectedIndex: Int,
    onSelectionChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 선택된 아이템의 실제 인덱스 찾기
    val actualSelectedIndex = remember(selectedIndex, items) {
        items.indexOf(selectedIndex).takeIf { it >= 0 } ?: 0
    }

    // 초기 스크롤 위치 설정
    LaunchedEffect(actualSelectedIndex) {
        listState.animateScrollToItem(actualSelectedIndex)
    }

    Box(
        modifier = modifier.height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        // 선택 영역 표시
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(
                    Color.Black.copy(alpha = 0.1f),
                    RoundedCornerShape(8.dp)
                )
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { Spacer(modifier = Modifier.height(40.dp)) } // 상단 패딩

            items(items.size) { index ->
                val item = items[index]
                val isSelected = actualSelectedIndex == index

                Text(
                    text = String.format("%02d", item),
                    fontSize = if (isSelected) 20.sp else 16.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Color.Black else Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .clickable {
                            onSelectionChanged(item)
                            scope.launch {
                                listState.animateScrollToItem(index)
                            }
                        }
                        .alpha(if (isSelected) 1f else 0.7f),
                )
            }

            item { Spacer(modifier = Modifier.height(40.dp)) } // 하단 패딩
        }
    }

    // 스크롤 위치에 따른 선택 업데이트
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (!listState.isScrollInProgress) {
            val centerIndex = listState.firstVisibleItemIndex
            if (centerIndex in items.indices && centerIndex != actualSelectedIndex) {
                onSelectionChanged(items[centerIndex])
            }
        }
    }
}