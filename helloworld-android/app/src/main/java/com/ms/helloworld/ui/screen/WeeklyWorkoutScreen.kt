package com.ms.helloworld.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ms.helloworld.dto.response.WorkoutItem
import com.ms.helloworld.dto.response.WorkoutType
import com.ms.helloworld.viewmodel.WeeklyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyWorkoutScreen(
    initialWeek: Int = 1,
    onBackClick: () -> Unit,
    viewModel: WeeklyViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(initialWeek) {
        viewModel.loadWeeklyData(initialWeek)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .pointerInput(state.currentWeek) {
                var totalDragAmount = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDragAmount = 0f },
                    onDragEnd = {
                        if (abs(totalDragAmount) > 100f) {
                            when {
                                totalDragAmount > 0 && state.currentWeek > 1 -> {
                                    viewModel.changeWeek(state.currentWeek - 1)
                                }
                                totalDragAmount < 0 && state.currentWeek < 42 -> {
                                    viewModel.changeWeek(state.currentWeek + 1)
                                }
                            }
                        }
                    }
                ) { _, dragAmount ->
                    totalDragAmount += dragAmount
                }
            }
    ) {
        // 상단 헤더 (뒤로가기 + 주차 표시)
        WeeklyWorkoutHeader(
            currentWeek = state.currentWeek,
            onBackClick = onBackClick
        )

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 주차별 운동 제목 (화살표 버튼 포함)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            onClick = { viewModel.changeWeek(state.currentWeek - 1) },
                            enabled = state.currentWeek > 1
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowLeft,
                                contentDescription = "이전 주차",
                                tint = if (state.currentWeek > 1) Color(0xFF333333) else Color(0xFFCCCCCC)
                            )
                        }

                        Text(
                            text = "${state.currentWeek}주차 루틴 추천",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF333333),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        IconButton(
                            onClick = { viewModel.changeWeek(state.currentWeek + 1) },
                            enabled = state.currentWeek < 42
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "다음 주차",
                                tint = if (state.currentWeek < 42) Color(0xFF333333) else Color(0xFFCCCCCC)
                            )
                        }
                    }

                    Text(
                        text = "임신 ${state.currentWeek}주차에 안전하고 효과적인 루틴들을 추천해드려요",
                        fontSize = 14.sp,
                        color = Color(0xFF666666),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
                }

                // 운동 목록
                items(state.workouts) { workout ->
                    WorkoutCard(workout = workout)
                }

                // 하단 여백
                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }

    // 에러 처리
    state.errorMessage?.let { error ->
        LaunchedEffect(error) {
            viewModel.clearError()
        }
    }
}

@Composable
private fun WeeklyWorkoutHeader(
    currentWeek: Int,
    onBackClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 뒤로가기 버튼 (좌측)
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "뒤로가기",
                tint = Color(0xFF333333)
            )
        }

        // 가운데 주차 표시
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${currentWeek}주차",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
                textAlign = TextAlign.Center
            )
        }

        // 우측 여백 (균형 맞추기)
        Spacer(modifier = Modifier.width(48.dp))
    }
}

@Composable
private fun WorkoutCard(workout: WorkoutItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // 운동 타입 아이콘
                val (icon, backgroundColor) = when (workout.type) {
                    WorkoutType.TEXT -> "📝" to Color(0xFFE8F5E8)
                    WorkoutType.VIDEO -> "📹" to Color(0xFFE1BEE7)
                }

                Box(
                    modifier = Modifier
                        .background(backgroundColor, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = icon,
                        fontSize = 24.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // 운동 제목
                    Text(
                        text = when (workout.type) {
                            WorkoutType.TEXT -> workout.text?.take(50) ?: "운동 가이드"
                            WorkoutType.VIDEO -> workout.title ?: "운동 영상"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 운동 타입 표시
                    Text(
                        text = when (workout.type) {
                            WorkoutType.TEXT -> "운동 가이드"
                            WorkoutType.VIDEO -> "영상 운동"
                        },
                        fontSize = 12.sp,
                        color = Color.White,
                        modifier = Modifier
                            .background(
                                when (workout.type) {
                                    WorkoutType.TEXT -> Color(0xFF4CAF50)
                                    WorkoutType.VIDEO -> Color(0xFF9C27B0)
                                },
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // 운동 내용
            when (workout.type) {
                WorkoutType.TEXT -> {
                    workout.text?.let { text ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = text,
                            fontSize = 14.sp,
                            color = Color(0xFF666666),
                            lineHeight = 20.sp
                        )
                    }
                }

                WorkoutType.VIDEO -> {
                    // 썸네일 이미지가 있으면 표시
                    workout.thumbnailUrl?.let { thumbnailUrl ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(thumbnailUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "운동 영상 썸네일",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                                placeholder = painterResource(id = android.R.drawable.ic_menu_gallery),
                                error = painterResource(id = android.R.drawable.ic_menu_gallery)
                            )

                            // 재생 버튼 오버레이
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Color.Black.copy(alpha = 0.3f),
                                        RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(Color.White, RoundedCornerShape(24.dp))
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "영상 재생",
                                        tint = Color(0xFF9C27B0),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 비디오 URL이 있으면 재생 버튼
                    workout.url?.let { url ->
                        if (workout.thumbnailUrl == null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "영상 재생",
                                        tint = Color(0xFF9C27B0)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "운동 영상 보기",
                                        fontSize = 14.sp,
                                        color = Color(0xFF9C27B0),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 순서 정보 (필요한 경우)
            workout.orderNo?.let { orderNo ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "순서: $orderNo",
                    fontSize = 12.sp,
                    color = Color(0xFF999999)
                )
            }
        }
    }
}