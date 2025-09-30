package com.ms.helloworld.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ms.helloworld.R
import com.ms.helloworld.dto.response.CalendarEventResponse
import com.ms.helloworld.ui.components.AddCalendarEventBottomSheet
import com.ms.helloworld.ui.components.CustomTopAppBar
import com.ms.helloworld.ui.components.EventDetailBottomSheet
import com.ms.helloworld.ui.theme.MainColor
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

// 색상 상수 정의 - 성능 최적화
private val PrimaryColor: Color = Color(0xFFF49699)
private val PrimaryColorLight: Color = Color(0xFFF49699).copy(alpha = 0.1f)
private val PrimaryColorMedium: Color = Color(0xFFF49699).copy(alpha = 0.3f)
private val PrimaryColorDark: Color = Color(0xFFF49699).copy(alpha = 0.6f)
private val PrimaryColorSemiLight: Color = Color(0xFFF49699).copy(alpha = 0.05f)

@SuppressLint("NewApi")
@Composable
fun CalendarScreen(
    navController: NavHostController,
    initialSelectedDate: String? = null,
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val backgroundColor = Color(0xFFFFFFFF)
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var selectedDateKey by remember { mutableStateOf("") }
    var editingEvent by remember { mutableStateOf<CalendarEventResponse?>(null) }
    var detailEvent by remember { mutableStateOf<CalendarEventResponse?>(null) }
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
        // 드래그 상태 강제 초기화
        draggingEvent = null
        isDragging = false
        dragOffset = 0f
    }
    
    // 에러 메시지 표시
    state.errorMessage?.let { errorMessage ->
        LaunchedEffect(errorMessage) {
            // 에러 표시 후 클리어
            viewModel.clearError()
        }
    }

    // 이벤트 상태 변경 감지하여 자동 새로고침
    LaunchedEffect(state.events) {
        // 이벤트 맵이 변경될 때마다 UI 자동 업데이트
        val totalEvents = state.events.values.sumOf { it.size }
        val currentDateEvents = state.events[displayDateKey]?.size ?: 0
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
                                Icon(
                                    painter = painterResource(R.drawable.left_arrow),
                                    contentDescription = "이전 달",
                                    tint = Color.Unspecified
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
                                Icon(
                                    painter = painterResource(R.drawable.ic_profile_move),
                                    contentDescription = "다음 달",
                                    tint = Color.Unspecified
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

            // 일정 목록 영역 (카드 섹션)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                // 섹션 헤더
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = PrimaryColorLight),
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (displayDateKey.isNotEmpty()) "${formatDateForDisplay(displayDateKey)} 일정" else "오늘 일정",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryColor
                        )
                        val currentEvents = (state.events[displayDateKey] ?: emptyList()).sortedBy { it.orderNo ?: Int.MAX_VALUE }
                        if (currentEvents.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = PrimaryColor),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = "${currentEvents.size}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 일정 목록 (orderNo 기준 정렬)
                val currentEvents = (state.events[displayDateKey] ?: emptyList()).sortedBy { it.orderNo ?: Int.MAX_VALUE }
                if (currentEvents.isEmpty() && !state.isLoading) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "📅",
                                    fontSize = 48.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "작성된 일정이 없습니다.",
                                    color = Color.Gray,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "+ 버튼을 눌러 새로운 일정을 추가해보세요.",
                                    color = Color.Gray,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else if (state.isLoading) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MainColor)
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(currentEvents.size, key = { index -> currentEvents[index].eventId }) { index ->
                                val event = currentEvents[index]
                                EventCard(
                                    event = event,
                                    onClick = {
                                        detailEvent = event
                                        showDetailDialog = true
                                    },
//                                    onDragStart = {
//                                        draggingEvent = event
//                                        isDragging = true
//                                    },
//                                    onDragEnd = { finalOffset ->
//                                        if (isDragging && draggingEvent != null) {
//                                            // 드래그 거리에 따라 순서 변경 결정
//                                            val threshold = 60f // 임계값을 줄여서 더 민감하게 반응
//                                            val currentIndex = currentEvents.indexOfFirst { it.eventId == draggingEvent!!.eventId }
//
//                                            if (currentIndex != -1) {
//                                                when {
//                                                    finalOffset < -threshold && currentIndex > 0 -> {
//                                                        // 위로 이동 (더 작은 orderNo로)
//                                                        val targetIndex = currentIndex - 1
//                                                        val draggedEvent = currentEvents[currentIndex]
//                                                        val targetEvent = currentEvents[targetIndex]
//
//                                                        // 새로운 리스트 생성하여 순서 재할당
//                                                        val reorderedEvents = currentEvents.toMutableList()
//                                                        reorderedEvents.removeAt(currentIndex)
//                                                        reorderedEvents.add(targetIndex, draggedEvent)
//
//                                                        // 전체 리스트의 orderNo를 1부터 순차적으로 재할당
//                                                        reorderedEvents.forEachIndexed { index, eventItem ->
//                                                            viewModel.updateEvent(
//                                                                eventId = eventItem.eventId,
//                                                                orderNo = index + 1
//                                                            )
//                                                        }
//                                                    }
//                                                    finalOffset > threshold && currentIndex < currentEvents.size - 1 -> {
//                                                        // 아래로 이동 (더 큰 orderNo로)
//                                                        val targetIndex = currentIndex + 1
//                                                        val draggedEvent = currentEvents[currentIndex]
//                                                        val targetEvent = currentEvents[targetIndex]
//
//                                                        // 새로운 리스트 생성하여 순서 재할당
//                                                        val reorderedEvents = currentEvents.toMutableList()
//                                                        reorderedEvents.removeAt(currentIndex)
//                                                        reorderedEvents.add(targetIndex, draggedEvent)
//
//                                                        // 전체 리스트의 orderNo를 1부터 순차적으로 재할당
//                                                        reorderedEvents.forEachIndexed { index, eventItem ->
//                                                            viewModel.updateEvent(
//                                                                eventId = eventItem.eventId,
//                                                                orderNo = index + 1
//                                                            )
//                                                        }
//                                                    }
//                                                }
//                                            }
//                                        }
//                                        // 드래그 상태 초기화
//                                        draggingEvent = null
//                                        isDragging = false
//                                        dragOffset = 0f
//                                    },
//                                    onDragUpdate = { offset ->
//                                        dragOffset = offset
//                                    },
//                                    isDragging = isDragging && draggingEvent?.eventId == event.eventId,
//                                    dragOffset = if (draggingEvent?.eventId == event.eventId) dragOffset else 0f
                                )
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
            containerColor = MainColor,
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

        AddCalendarEventBottomSheet(
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

                    // 모든 필드를 명시적으로 전달 (null 방지)
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

    // 이벤트 상세 다이얼로그
    if (showDetailDialog && detailEvent != null) {
        EventDetailBottomSheet(
            event = detailEvent!!,
            onDismiss = {
                showDetailDialog = false
                detailEvent = null
            },
            onEdit = {
                // 상세 다이얼로그를 닫고 편집 다이얼로그 열기
                editingEvent = detailEvent
                editTitle = detailEvent!!.title
                editContent = detailEvent!!.memo ?: ""
                editStartTime = try {
                    detailEvent!!.startAt.substring(11, 16)
                } catch (e: Exception) { "09:00" }
                editEndTime = try {
                    detailEvent!!.endAt?.substring(11, 16) ?: "10:00"
                } catch (e: Exception) { "10:00" }
                editIsRemind = detailEvent!!.remind
                editOrderNo = detailEvent!!.orderNo ?: 1
                selectedDateKey = displayDateKey
                showDetailDialog = false
                detailEvent = null
                showAddDialog = true
            },
            onDelete = {
                val eventIdToDelete = detailEvent!!.eventId
                viewModel.deleteEvent(eventIdToDelete)
                showDetailDialog = false
                detailEvent = null
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
                                            isSelected -> MainColor
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
                                                    if (isSelected) Color.White else PrimaryColor,
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
    onClick: () -> Unit = {},
    onDragStart: () -> Unit = {},
    onDragEnd: (Float) -> Unit = {},
    onDragUpdate: (Float) -> Unit = {},
    isDragging: Boolean = false,
    dragOffset: Float = 0f
) {
    var isLongPressed by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = dragOffset
                alpha = if (isDragging) 0.9f else 1f
                scaleX = if (isDragging) 1.02f else 1f
                scaleY = if (isDragging) 1.02f else 1f
                rotationZ = if (isDragging) (dragOffset / 50f).coerceIn(-2f, 2f) else 0f
            }
            .animateContentSize()
//            .pointerInput(event.eventId) {
//                var totalOffset = 0f
//                detectDragGesturesAfterLongPress(
//                    onDragStart = {
//                        totalOffset = 0f
//                        isLongPressed = true
//                        onDragStart()
//                    },
//                    onDragEnd = {
//                        onDragEnd(totalOffset)
//                        isLongPressed = false
//                    },
//                    onDrag = { change, dragAmount ->
//                        totalOffset += dragAmount.y
//                        onDragUpdate(totalOffset)
//                        change.consume() // 제스처 소비 추가
//                    }
//                )
//            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                enabled = !isDragging // 드래그 중에는 클릭 비활성화
            ) {
                if (!isLongPressed) { // 길게 누르지 않았을 때만 클릭
                    onClick()
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDragging -> PrimaryColorLight
                isLongPressed -> PrimaryColorSemiLight
                else -> Color(0xFFF8F9FA)
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 16.dp else 0.dp
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isDragging) BorderStroke(2.dp, PrimaryColorMedium) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 드래그 핸들과 제목 영역
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // 드래그 핸들 (길게 누를 때만 표시)
//                if (isDragging || isLongPressed) {
//                    Column(
//                        modifier = Modifier.padding(end = 12.dp, top = 4.dp)
//                    ) {
//                        repeat(3) {
//                            Box(
//                                modifier = Modifier
//                                    .size(width = 3.dp, height = 12.dp)
//                                    .background(
//                                        PrimaryColorDark,
//                                        RoundedCornerShape(2.dp)
//                                    )
//                            )
//                            if (it < 2) Spacer(modifier = Modifier.height(2.dp))
//                        }
//                    }
//                }

                // 메인 콘텐츠
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF1A1A1A)
                    )

                    if (!event.memo.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = event.memo,
                            fontSize = 14.sp,
                            color = Color(0xFF666666),
                            lineHeight = 20.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 시간과 순서 정보
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_time),
                            contentDescription = "시간",
                            modifier = Modifier.size(16.dp),
                            tint = PrimaryColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val timeFormat = try {
                            val startTime = event.startAt.substring(11, 16)
                            val endTime = event.endAt?.substring(11, 16)
                            if (endTime != null) "$startTime - $endTime" else startTime
                        } catch (e: Exception) {
                            "시간 정보 없음"
                        }
                        Text(
                            text = timeFormat,
                            fontSize = 12.sp,
                            color = Color(0xFF666666),
                            fontWeight = FontWeight.Medium
                        )

                        // 순서 표시 (드래그 모드일 때)
//                        if (isDragging) {
//                            Spacer(modifier = Modifier.width(12.dp))
//                            Card(
//                                colors = CardDefaults.cardColors(containerColor = PrimaryColor),
//                                shape = CircleShape
//                            ) {
//                                Text(
//                                    text = "#${event.orderNo ?: 1}",
//                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
//                                    color = Color.White,
//                                    fontSize = 10.sp,
//                                    fontWeight = FontWeight.Bold
//                                )
//                            }
//                        }
                    }
                }
            }


            // 드래그 안내 메시지 (길게 누를 때만 표시)
//            if (isLongPressed && !isDragging) {
//                Spacer(modifier = Modifier.height(8.dp))
//                Card(
//                    colors = CardDefaults.cardColors(containerColor = PrimaryColorLight),
//                    shape = RoundedCornerShape(8.dp)
//                ) {
//                    Text(
//                        text = "↕️ 위아래로 드래그하여 순서를 변경하세요",
//                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
//                        fontSize = 11.sp,
//                        color = PrimaryColor,
//                        fontWeight = FontWeight.Medium,
//                        textAlign = TextAlign.Center
//                    )
//                }
//            }
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

