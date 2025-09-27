package com.ms.helloworld.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun SimpleKoreanTimePicker(
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    is24Hour: Boolean = true,     // 24시간제 기본
    minuteStep: Int = 1           // 1, 5, 10 등으로 분 단위 간격 설정
) {
    var hour by remember(time, is24Hour) {
        mutableIntStateOf(if (is24Hour) time.hour else (if (time.hour % 12 == 0) 12 else time.hour % 12))
    }
    var minute by remember(time) { mutableIntStateOf(time.minute - (time.minute % minuteStep)) }
    var isAm by remember(time, is24Hour) { mutableStateOf(if (is24Hour) true else time.hour < 12) }

    // 아이템 목록
    val hours24 = (0..23).map { it.toString().padStart(2, '0') }
    val hours12 = (1..12).map { it.toString().padStart(2, '0') }
    val minutes = (0..59 step minuteStep).map { it.toString().padStart(2, '0') }
    val ampm = listOf("AM", "PM")

    // 선택 변경 시 LocalTime로 변환하여 콜백
    fun emit() {
        val h24 = if (is24Hour) {
            hour
        } else {
            val base = hour % 12
            if (isAm) base else base + 12
        }.let { if (it == 24) 0 else it }
        onTimeChange(LocalTime.of(h24, minute))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("시간을 선택해 주세요", fontSize = 18.sp, color = Color.Black, modifier = Modifier.padding(bottom = 16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!is24Hour) {
                // AM/PM 휠
                CustomWheelPicker( // ← 기존 휠 재사용
                    items = ampm,
                    selectedIndex = if (isAm) 0 else 1,
                    onSelectionChanged = { idx -> isAm = (idx == 0); emit() },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
            }

            // 시 휠
            CustomWheelPicker(
                items = if (is24Hour) hours24 else hours12,
                selectedIndex = if (is24Hour) hour else hours12.indexOf(hour.toString().padStart(2, '0')).coerceAtLeast(0),
                onSelectionChanged = { idx ->
                    hour = if (is24Hour) idx else (idx + 1)  // 12시간제는 1..12
                    emit()
                },
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(8.dp))

            // 분 휠
            CustomWheelPicker(
                items = minutes,
                selectedIndex = minutes.indexOf(minute.toString().padStart(2, '0')).coerceAtLeast(0),
                onSelectionChanged = { idx ->
                    minute = minutes[idx].toInt()
                    emit()
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
@Composable
fun CustomWheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectionChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleItemsCount: Int = 3
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val itemHeight = 44.dp // iOS 스타일에 맞게 조정
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // 선택 변경 시 햅틱 피드백
    LaunchedEffect(selectedIndex) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // 실시간 스크롤 오프셋 계산
    val currentScrollOffset by remember {
        derivedStateOf {
            if (listState.layoutInfo.totalItemsCount == 0) selectedIndex.toFloat()
            else listState.firstVisibleItemScrollOffset.toFloat() / itemHeightPx + listState.firstVisibleItemIndex
        }
    }

    // 스크롤 종료 시 자동 스냅
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val centerIndex = currentScrollOffset.roundToInt().coerceIn(0, items.size - 1)

            // 부드러운 스냅 애니메이션
            if (abs(listState.firstVisibleItemScrollOffset) > 5) {
                coroutineScope.launch {
                    listState.animateScrollToItem(centerIndex)
                }
            }

            if (centerIndex != selectedIndex) {
                onSelectionChanged(centerIndex)
            }
        }
    }

    Box(
        modifier = modifier.height(itemHeight * visibleItemsCount),
        contentAlignment = Alignment.Center
    ) {
        // 3D 원기둥 배경 효과 (더 미묘하게)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.01f),
                            Color.Black.copy(alpha = 0.02f),
                            Color.Black.copy(alpha = 0.01f),
                            Color.Transparent
                        )
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
        )

        // 선택 영역 하이라이트 (iOS 스타일에 맞게 조정)
        Box(
            modifier = Modifier
                .height(itemHeight)
                .fillMaxWidth(0.9f)
                .background(
                    Color(0xFFE8E8E8).copy(alpha = 0.4f), // 더 연한 회색
                    RoundedCornerShape(8.dp)
                )
                .border(
                    0.5.dp, // 더 얇은 테두리
                    Color(0xFFD0D0D0).copy(alpha = 0.5f),
                    RoundedCornerShape(8.dp)
                )
        )

        LazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = itemHeight * (visibleItemsCount / 2))
        ) {
            itemsIndexed(items) { index, item ->
                val centerOffset = currentScrollOffset - index
                val distanceFromCenter = abs(centerOffset).toFloat()

                // 3D 원기둥 효과 계산
                val maxDistance = visibleItemsCount / 2f
                val normalizedDistance = (distanceFromCenter / maxDistance).coerceIn(0f, 1f)

                // 투명도 곡선 (iOS 스타일에 맞게 조정)
                val alpha = max(0.15f, cos(normalizedDistance * PI / 2).toFloat().pow(0.8f))

                // 크기 곡선 (더 자연스럽게)
                val scale = max(0.65f, cos(normalizedDistance * PI / 3).toFloat().pow(0.9f))

                // 회전 각도 (iOS와 더 유사하게)
                val rotationX = centerOffset * 25f

                // 깊이감을 위한 Y축 이동
                val translationY = sin(Math.toRadians(rotationX.toDouble())).toFloat() * 10f

                Text(
                    text = item,
                    fontSize = (18 * scale).sp, // 크기 조정
                    fontWeight = if (normalizedDistance < 0.1f)
                        FontWeight.SemiBold // iOS 스타일 폰트 웨이트
                    else
                        FontWeight.Normal,
                    color = if (normalizedDistance < 0.1f)
                        Color.Black.copy(alpha = 0.9f)
                    else
                        Color.Black.copy(alpha = alpha * 0.7f),
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .graphicsLayer {
                            this.alpha = alpha
                            this.scaleX = scale
                            this.scaleY = scale
                            this.rotationX = rotationX
                            this.translationY = translationY

                            // 더 깊은 원근감
                            cameraDistance = 12f * density
                        }
                        .wrapContentHeight(Alignment.CenterVertically),
                    textAlign = TextAlign.Center
                )
            }
        }

        // iOS 스타일 상하단 페이드 효과
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.9f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Transparent,
                            Color.White.copy(alpha = 0.9f)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )
    }
}
@Composable
fun KoreanTimePickerDialog(
    time: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    is24Hour: Boolean = true,
    minuteStep: Int = 1
) {
    var tempTime by remember(time) { mutableStateOf(time) }

    Dialog(onDismissRequest = onDismiss) { // 바깥 탭으로 닫기 허용 (기존 DatePickerDialog와 동일)
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(48.dp)) // iOS 느낌 라운드
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SimpleKoreanTimePicker(
                time = tempTime,
                onTimeChange = { tempTime = it },
                is24Hour = is24Hour,
                minuteStep = minuteStep,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("취소", fontSize = 17.sp, color = Color(0xFF8E8E93))
                }
                TextButton(onClick = { onTimeSelected(tempTime); onDismiss() }) {
                    Text("저장", fontSize = 17.sp, color = Color.Black)
                }
            }
        }
    }
}

/** 편의용 래퍼 */
@Composable
fun KoreanTimePicker(
    time: LocalTime = LocalTime.now(),
    onTimeSelected: (LocalTime) -> Unit = {},
    onDismiss: () -> Unit = {},
    is24Hour: Boolean = true,
    minuteStep: Int = 1
) {
    KoreanTimePickerDialog(
        time = time,
        onTimeSelected = onTimeSelected,
        onDismiss = onDismiss,
        is24Hour = is24Hour,
        minuteStep = minuteStep
    )
}

/** AddCalendarEventDialog과의 호환성을 위한 래퍼 */
@Composable
fun CustomTimePickerDialog(
    initialTime: String,
    onTimeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // String을 LocalTime으로 변환
    val parsedTime = try {
        val parts = initialTime.split(":")
        LocalTime.of(parts[0].toInt(), parts[1].toInt())
    } catch (e: Exception) {
        LocalTime.of(9, 0) // 기본값
    }

    KoreanTimePickerDialog(
        time = parsedTime,
        onTimeSelected = { selectedTime ->
            // LocalTime을 String으로 변환 (HH:mm 형식)
            val formattedTime = String.format("%02d:%02d", selectedTime.hour, selectedTime.minute)
            onTimeSelected(formattedTime)
        },
        onDismiss = onDismiss,
        is24Hour = true,
        minuteStep = 1
    )
}
