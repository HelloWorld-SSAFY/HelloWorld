package com.ms.helloworld.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.ms.helloworld.dto.response.CalendarPost
import com.ms.helloworld.navigation.Screen
import com.ms.helloworld.ui.components.AddPostDialog
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    navController: NavHostController,
    initialSelectedDate: String? = null
) {
    val backgroundColor = Color(0xFFFAEDBA)
    var posts by remember { mutableStateOf(mapOf<String, List<CalendarPost>>()) }
    var showBottomSheet by remember { mutableStateOf(initialSelectedDate != null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedDateKey by remember { mutableStateOf(initialSelectedDate ?: "") }

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
            TopAppBar(
                title = {
                    Text(
                        "일정 관리",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.navigate(Screen.HomeScreen.route) {
                            popUpTo(Screen.HomeScreen.route) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "뒤로 가기"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor
                )
            )
            // 캘린더 영역
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (showBottomSheet) 0.6f else 1f)
                    .padding(16.dp)
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
                            postsMap = posts,
                            onDateClick = { dateKey, dateString ->
                                selectedDate = dateString
                                selectedDateKey = dateKey
                                showBottomSheet = true
                            }
                        )
                    }
                }
            }

            // 바텀시트 영역 (non-modal)
            if (showBottomSheet && selectedDateKey.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.4f)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // 바텀시트 핸들과 헤더
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${formatDateForDisplay(selectedDateKey)} 일기",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { showBottomSheet = false }
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = "닫기"
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 게시글 목록
                        val currentPosts = posts[selectedDateKey] ?: emptyList()
                        if (currentPosts.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "작성된 일기가 없습니다.",
                                        color = Color.Gray,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "플로팅 버튼을 눌러 일기를 작성해보세요!",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(currentPosts) { post ->
                                    PostCard(
                                        post = post,
                                        onDelete = {
                                            val updatedPosts = currentPosts.filter { it.id != post.id }
                                            posts = if (updatedPosts.isEmpty()) {
                                                posts - selectedDateKey
                                            } else {
                                                posts + (selectedDateKey to updatedPosts)
                                            }
                                        }
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
            onClick = { showAddDialog = true },
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
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
        }

        AddPostDialog(
            selectedDate = dateKeyToUse,
            onDismiss = { showAddDialog = false },
            onSave = { title, content ->
                val newPost = CalendarPost(
                    id = UUID.randomUUID().toString(),
                    date = dateKeyToUse,
                    title = title,
                    content = content
                )
                val currentPosts = posts[dateKeyToUse] ?: emptyList()
                posts = posts + (dateKeyToUse to (currentPosts + newPost))
                showAddDialog = false
                selectedDateKey = dateKeyToUse
            }
        )
    }
}

@Composable
fun CalendarGrid(
    displayCalendar: Calendar,
    selectedDate: String,
    postsMap: Map<String, List<CalendarPost>>,
    onDateClick: (String, String) -> Unit
) {
    val monthCalendar = Calendar.getInstance().apply {
        time = displayCalendar.time
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val firstDayOfWeek = monthCalendar.get(Calendar.DAY_OF_WEEK) - 1 // 0=일요일
    val daysInMonth = monthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val today = Calendar.getInstance()

    val weeks = mutableListOf<List<Calendar?>>()
    var currentWeek = mutableListOf<Calendar?>()

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
        currentWeek.add(dayCalendar)
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

    Column {
        weeks.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                week.forEach { date ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (date != null) {
                            val dateString = date.get(Calendar.DAY_OF_MONTH).toString()
                            val isSelected = dateString == selectedDate &&
                                           date.get(Calendar.MONTH) == displayCalendar.get(Calendar.MONTH)
                            val isToday = date.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                                         date.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                                         date.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)
                            val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date.time)
                            val hasPost = postsMap[dateKey]?.isNotEmpty() == true

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
                                    ) { onDateClick(dateKey, dateString) },
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
                                    if (hasPost) {
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
fun PostCard(
    post: CalendarPost,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F8F8)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                    if (post.content.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = post.content,
                            fontSize = 14.sp,
                            color = Color.Gray,
                            lineHeight = 20.sp
                        )
                    }
                }

                TextButton(
                    onClick = onDelete,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("삭제", fontSize = 12.sp, color = Color.Red)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis())),
                fontSize = 12.sp,
                color = Color.Gray
            )
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