package com.ms.helloworld.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ms.helloworld.dto.response.CalendarEventResponse
import com.ms.helloworld.ui.components.AddCalendarEventDialog
import com.ms.helloworld.ui.components.CustomTopAppBar
import com.ms.helloworld.viewmodel.CalendarViewModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.*

// 달력 데이터 클래스 - 성능 최적화를 위해 추가
data class CalendarData(
    val year: Int,
    val month: Int,
    val day: Int,
    val dateKey: String
)

@SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    navController: NavHostController,
    initialSelectedDate: String? = null,
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val backgroundColor = Color(0xFFFFFFFF)
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedDateKey by remember { mutableStateOf("") }
    var editingEvent by remember { mutableStateOf<CalendarEventResponse?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf("") }
    var editStartTime by remember { mutableStateOf("09:00") }
    var editEndTime by remember { mutableStateOf("10:00") }
    var editIsRemind by remember { mutableStateOf(false) }
    var editOrderNo by remember { mutableStateOf(1) }

    // 드래그 앤 드롭 관련 상태
    var draggingEvent by remember { mutableStateOf<CalendarEventResponse?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    // 기본적으로 오늘 날짜의 일정을 표시
    val today = LocalDate.now().toString()
    var displayDateKey by remember { mutableStateOf(initialSelectedDate ?: today) }
    
    // 초기 날짜 설정
    LaunchedEffect(initialSelectedDate) {
        initialSelectedDate?.let {
            viewModel.selectDate(it)
            displayDateKey = it
        }
    }
    
    // 에러 메시지 표시
    state.errorMessage?.let { errorMessage ->
        LaunchedEffect(errorMessage) {
            // 에러 발생 시 스낵바나 토스트 표시 가능
            // 여기서는 콘솔에 로그만 출력
            println("Calendar Error: $errorMessage")
            // 에러 표시 후 클리어
            viewModel.clearError()
        }
    }

    var displayCalendar by remember {
        mutableStateOf(
            if (initialSelectedDate != null) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                Calendar.getInstance().apply {
                    time = dateFormat.parse(initialSelectedDate) ?: Date()
                }
            } else {
                Calendar.getInstance()
            }
        )
    }
    var selectedDate by remember {
        mutableStateOf(
            if (initialSelectedDate != null) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = dateFormat.parse(initialSelectedDate)
                if (date != null) {
                    Calendar.getInstance().apply { time = date }.get(Calendar.DAY_OF_MONTH).toString()
                } else {
                    Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString()
                }
            } else {
                Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString()
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 상단 앱바
            CustomTopAppBar(
                title = "calendar",
                navController = navController,
            )
            // 캘린더 영역
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f)

            ) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // 헤더 (월/년도 및 네비게이션)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                displayCalendar = Calendar.getInstance().apply {
                                    time = displayCalendar.time
                                    add(Calendar.MONTH, -1)
                                }
                            }) {
                                Text(
                                    "<",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                SimpleDateFormat("yyyy년 MM월", Locale.getDefault()).format(displayCalendar.time),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(onClick = {
                                displayCalendar = Calendar.getInstance().apply {
                                    time = displayCalendar.time
                                    add(Calendar.MONTH, 1)
                                }
                            }) {
                                Text(
                                    ">",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 요일 헤더
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            val dayHeaders = listOf("일", "월", "화", "수", "목", "금", "토")
                            dayHeaders.forEach { day ->
                                Text(
                                    text = day,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 캘린더 그리드
                        CalendarGrid(
                            displayCalendar = displayCalendar,
                            selectedDate = selectedDate,
                            eventsMap = state.events,
                            onDateClick = { dateKey, dateString ->
                                selectedDate = dateString
                                displayDateKey = dateKey
                                viewModel.selectDate(dateKey)
                            }
                        )
                    }
                }
            }

            // 일정 목록 영역
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // 헤더
                    Text(
                        text = if (displayDateKey.isNotEmpty()) "${formatDateForDisplay(displayDateKey)} 일정" else "오늘 일정",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 일정 목록 (orderNo 기준 정렬)
                    val currentEvents = (state.events[displayDateKey] ?: emptyList()).sortedBy { it.orderNo ?: Int.MAX_VALUE }
                    if (currentEvents.isEmpty() && !state.isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "작성된 일정이 없습니다.",
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "플로팅 버튼을 눌러 일정을 추가해보세요.",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else if (state.isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(currentEvents.size, key = { index -> currentEvents[index].eventId }) { index ->
                                val event = currentEvents[index]

                                Column {
                                    EventCard(
                                        event = event,
                                        onEdit = {
                                            editingEvent = event
                                            editTitle = event.title
                                            editContent = event.memo ?: ""
                                            // ISO 8601에서 시간 추출
                                            editStartTime = try {
                                                event.startAt.substring(11, 16)
                                            } catch (e: Exception) { "09:00" }
                                            editEndTime = try {
                                                event.endAt?.substring(11, 16) ?: "10:00"
                                            } catch (e: Exception) { "10:00" }
                                            editIsRemind = event.remind
                                            editOrderNo = event.orderNo ?: 1
                                            selectedDateKey = displayDateKey
                                            showAddDialog = true
                                        },
                                        onDelete = {
                                            viewModel.deleteEvent(event.eventId)
                                        },
                                        onDragStart = {
                                            draggingEvent = event
                                            isDragging = true
                                        },
                                        onDragEnd = { offset ->
                                            if (isDragging && draggingEvent != null) {
                                                // 드래그 거리에 따라 순서 변경 결정
                                                val threshold = 100f // 100픽셀 이상 드래그해야 순서 변경
                                                val currentIndex = currentEvents.indexOfFirst { it.eventId == draggingEvent!!.eventId }

                                                when {
                                                    offset < -threshold && currentIndex > 0 -> {
                                                        // 위로 이동
                                                        val targetEvent = currentEvents[currentIndex - 1]
                                                        swapEventOrders(draggingEvent!!, targetEvent, viewModel)
                                                    }
                                                    offset > threshold && currentIndex < currentEvents.size - 1 -> {
                                                        // 아래로 이동
                                                        val targetEvent = currentEvents[currentIndex + 1]
                                                        swapEventOrders(draggingEvent!!, targetEvent, viewModel)
                                                    }
                                                }
                                            }
                                            // 드래그 상태 초기화
                                            draggingEvent = null
                                            isDragging = false
                                            dragOffset = 0f
                                        },
                                        isDragging = isDragging && draggingEvent?.eventId == event.eventId,
                                        dragOffset = if (draggingEvent?.eventId == event.eventId) dragOffset else 0f
                                    )

                                }
                            }
                        }
                    }
                }
            }
        }

        // 플로팅 버튼 (항상 위에 표시)
        FloatingActionButton(
            onClick = {
                editingEvent = null
                editTitle = ""
                editContent = ""
                editStartTime = "09:00"
                editEndTime = "10:00"
                editIsRemind = false
                editOrderNo = 1
                selectedDateKey = displayDateKey
                showAddDialog = true
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .zIndex(1f)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "일정 추가"
            )
        }
    }

    // 다이얼로그 - 게시글 추가
    if (showAddDialog) {
        val dateKeyToUse = if (selectedDateKey.isNotEmpty()) {
            selectedDateKey
        } else {
            displayDateKey.ifEmpty {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
            }
        }

        AddCalendarEventDialog(
            selectedDate = dateKeyToUse,
            initialTitle = editTitle,
            initialContent = editContent,
            initialStartTime = editStartTime,
            initialEndTime = editEndTime,
            initialIsRemind = editIsRemind,
            initialOrderNo = editOrderNo,
            onDismiss = { 
                showAddDialog = false
                editingEvent = null
            },
            onSave = { title, content, startTime, endTime, isRemind, orderNo ->
                if (editingEvent != null) {
                    // 수정 모드 - ISO 8601 형식으로 변환
                    val startAt = "${dateKeyToUse}T${startTime}:00Z"
                    val endAt = "${dateKeyToUse}T${endTime}:00Z"

                    viewModel.updateEvent(
                        eventId = editingEvent!!.eventId,
                        title = title,
                        content = content,
                        startAt = startAt,
                        endAt = endAt,
                        isRemind = isRemind,
                        orderNo = orderNo
                    )
                } else {
                    // 새로 추가 모드 - ISO 8601 형식으로 변환
                    val startAt = "${dateKeyToUse}T${startTime}:00Z"
                    val endAt = "${dateKeyToUse}T${endTime}:00Z"

                    // 해당 날짜의 기존 일정 중 최대 orderNo 찾아서 +1
                    val existingEvents = state.events[dateKeyToUse] ?: emptyList()
                    val maxOrderNo = existingEvents.maxOfOrNull { it.orderNo ?: 0 } ?: 0
                    val newOrderNo = maxOrderNo + 1

                    viewModel.createEvent(
                        title = title,
                        content = content,
                        startAt = startAt,
                        endAt = endAt,
                        isRemind = isRemind,
                        orderNo = newOrderNo
                    )
                }
                showAddDialog = false
                editingEvent = null
                selectedDateKey = dateKeyToUse
            }
        )
    }

}

@Composable
fun CalendarGrid(
    displayCalendar: Calendar,
    selectedDate: String,
    eventsMap: Map<String, List<CalendarEventResponse>>,
    onDateClick: (String, String) -> Unit
) {
    // 달력 데이터를 remember로 캐시하여 불필요한 재계산 방지
    val calendarData = remember(displayCalendar.get(Calendar.YEAR), displayCalendar.get(Calendar.MONTH)) {
        val monthCalendar = Calendar.getInstance().apply {
            time = displayCalendar.time
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val firstDayOfWeek = monthCalendar.get(Calendar.DAY_OF_WEEK) - 1 // 0=일요일
        val daysInMonth = monthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        val weeks = mutableListOf<List<CalendarData?>>()
        var currentWeek = mutableListOf<CalendarData?>()

        // 첫 주의 빈 날짜들
        repeat(firstDayOfWeek) {
            currentWeek.add(null)
        }

        // 월의 모든 날짜들
        for (day in 1..daysInMonth) {
            val dayCalendar = Calendar.getInstance().apply {
                time = displayCalendar.time
                set(Calendar.DAY_OF_MONTH, day)
            }
            val calendarData = CalendarData(
                year = dayCalendar.get(Calendar.YEAR),
                month = dayCalendar.get(Calendar.MONTH),
                day = dayCalendar.get(Calendar.DAY_OF_MONTH),
                dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(dayCalendar.time)
            )
            currentWeek.add(calendarData)
            if (currentWeek.size == 7) {
                weeks.add(currentWeek.toList())
                currentWeek = mutableListOf()
            }
        }

        // 마지막 주의 빈 날짜들
        if (currentWeek.isNotEmpty()) {
            while (currentWeek.size < 7) {
                currentWeek.add(null)
            }
            weeks.add(currentWeek.toList())
        }
        weeks
    }

    // 오늘 날짜 정보도 remember로 캐시
    val today = remember {
        val cal = Calendar.getInstance()
        Triple(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
    }

    // 주의 개수에 따라 높이를 동적으로 조정
    val weekCount = calendarData.size
    val cellHeight = if (weekCount > 5) 35.dp else 42.dp
    
    Column {
        calendarData.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                week.forEach { dateData ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(cellHeight)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dateData != null) {
                            val dateString = dateData.day.toString()
                            val isSelected = dateString == selectedDate &&
                                           dateData.month == displayCalendar.get(Calendar.MONTH)
                            val isToday = dateData.year == today.first &&
                                         dateData.month == today.second &&
                                         dateData.day == today.third
                            val hasEvent = eventsMap[dateData.dateKey]?.isNotEmpty() == true

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSelected -> Color.Black
                                            isToday -> Color.Gray.copy(alpha = 0.3f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = LocalIndication.current
                                    ) { onDateClick(dateData.dateKey, dateString) },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = dateString,
                                        fontSize = 14.sp,
                                        color = if (isSelected) Color.White else Color.Black,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (hasEvent) {
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .background(
                                                    if (isSelected) Color.White else Color.Blue,
                                                    CircleShape
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EventCard(
    event: CalendarEventResponse,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: (Float) -> Unit = {},
    isDragging: Boolean = false,
    dragOffset: Float = 0f
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .graphicsLayer {
                translationY = dragOffset
                alpha = if (isDragging) 0.8f else 1f
            }
            .shadow(
                elevation = if (isDragging) 8.dp else 0.dp,
                shape = RoundedCornerShape(8.dp)
            )
            .background(
                color = if (isDragging) Color.LightGray.copy(alpha = 0.1f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .pointerInput(event.eventId) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        onDragStart()
                    },
                    onDragEnd = {
                        onDragEnd(dragOffset)
                    },
                    onDrag = { change, _ ->
                        onDragEnd(change.position.y)
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.Black
                )
                if (!event.memo.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = event.memo,
                        fontSize = 14.sp,
                        color = Color.Gray,
                        lineHeight = 20.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // ISO 8601 날짜를 시간으로 변환하여 표시
                val timeFormat = try {
                    val startTime = event.startAt.substring(11, 16) // "HH:mm"
                    val endTime = event.endAt?.substring(11, 16)
                    if (endTime != null) "$startTime - $endTime" else startTime
                } catch (e: Exception) {
                    "시간 정보 없음"
                }
                Text(
                    text = timeFormat,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Row {
                TextButton(
                    onClick = onEdit,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("수정", fontSize = 12.sp, color = Color.Blue)
                }
                
                TextButton(
                    onClick = onDelete,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("삭제", fontSize = 12.sp, color = Color.Red)
                }
            }
        }
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

// 두 이벤트의 orderNo를 교체하는 헬퍼 함수
private fun swapEventOrders(
    event1: CalendarEventResponse,
    event2: CalendarEventResponse,
    viewModel: CalendarViewModel
) {
    // 두 이벤트의 orderNo를 교체
    val order1 = event1.orderNo ?: 1
    val order2 = event2.orderNo ?: 2

    // event1을 event2의 orderNo로 업데이트
    viewModel.updateEvent(
        eventId = event1.eventId,
        title = event1.title,
        content = event1.memo ?: "",
        startAt = event1.startAt,
        endAt = event1.endAt ?: event1.startAt,
        isRemind = event1.remind,
        orderNo = order2
    )

    // event2를 event1의 orderNo로 업데이트
    viewModel.updateEvent(
        eventId = event2.eventId,
        title = event2.title,
        content = event2.memo ?: "",
        startAt = event2.startAt,
        endAt = event2.endAt ?: event2.startAt,
        isRemind = event2.remind,
        orderNo = order1
    )
}