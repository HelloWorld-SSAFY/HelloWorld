package com.ms.helloworld.ui.screen

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ms.helloworld.ui.components.*
import com.ms.helloworld.data.CalendarPost
import com.ms.helloworld.data.MomProfile
import com.ms.helloworld.ui.viewmodel.HomeViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavHostController
import java.util.UUID
import java.time.LocalDate

@SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = viewModel()
) {
    val backgroundColor = Color(0xFFFAEDBA)
    var showFullCalendar by remember { mutableStateOf(false) }
    var posts by remember { mutableStateOf(mapOf<String, List<CalendarPost>>()) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedDateKey by remember { mutableStateOf("") }
    val bottomSheetState = rememberModalBottomSheetState()
    
    val momProfile by viewModel.momProfile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "로고",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = "알림"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 프로필 섹션 (항상 표시)
            if (isLoading) {
                ProfileSection(
                    momProfile = MomProfile(
                        nickname = "로딩중...",
                        pregnancyWeek = 1,
                        dueDate = LocalDate.now()
                    )
                )
            } else {
                ProfileSection(momProfile = momProfile)
            }

            // 캘린더 섹션 (확장/축소 가능) - 부드러운 크기 변화
            Box(
                modifier = Modifier.animateContentSize(
                    animationSpec = tween(
                        durationMillis = 500,
                        easing = EaseOutCubic
                    )
                )
            ) {
                if (showFullCalendar) {
                    ExpandedCalendarSection(
                        onCollapse = { showFullCalendar = false },
                        onDateClick = { dateKey ->
                            selectedDateKey = dateKey
                            showBottomSheet = true
                        },
                        postsMap = posts
                    )
                } else {
                    CalendarSection(
                        onExpand = { showFullCalendar = true },
                        onDateClick = { dateKey ->
                            selectedDateKey = dateKey
                            showBottomSheet = true
                        },
                        postsMap = posts
                    )
                }
            }

            // 오늘의 추천 섹션
            TodayRecommendationSection()

            // 엄마의 건강상태 섹션
            HealthStatusSection()
        }
    }
    
    // 바텀시트 - 게시글 목록
    if (showBottomSheet && selectedDateKey.isNotEmpty()) {
        CalendarPostBottomSheet(
            selectedDate = selectedDateKey,
            posts = posts[selectedDateKey] ?: emptyList(),
            onDismiss = { showBottomSheet = false },
            onAddPost = { showAddDialog = true },
            onDeletePost = { postToDelete ->
                val currentPosts = posts[selectedDateKey] ?: emptyList()
                val updatedPosts = currentPosts.filter { it.id != postToDelete.id }
                posts = if (updatedPosts.isEmpty()) {
                    posts - selectedDateKey
                } else {
                    posts + (selectedDateKey to updatedPosts)
                }
            },
            bottomSheetState = bottomSheetState
        )
    }
    
    // 다이얼로그 - 게시글 추가
    if (showAddDialog && selectedDateKey.isNotEmpty()) {
        AddPostDialog(
            selectedDate = selectedDateKey,
            onDismiss = { showAddDialog = false },
            onSave = { title, content ->
                val newPost = CalendarPost(
                    id = UUID.randomUUID().toString(),
                    date = selectedDateKey,
                    title = title,
                    content = content
                )
                val currentPosts = posts[selectedDateKey] ?: emptyList()
                posts = posts + (selectedDateKey to (currentPosts + newPost))
                showAddDialog = false
            }
        )
    }
}


@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    HomeScreen(navController = null as NavHostController)
}