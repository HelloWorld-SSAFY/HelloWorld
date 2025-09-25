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
import com.ms.helloworld.dto.response.DietDay
import com.ms.helloworld.viewmodel.WeeklyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyDietScreen(
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
        WeeklyDietHeader(
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 주차별 식단 제목 (화살표 버튼 포함)
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
                            text = "${state.currentWeek}주차 음식 추천",
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
                        text = "임신 ${state.currentWeek}주차에 좋은 음식들을 추천해드려요",
                        fontSize = 14.sp,
                        color = Color(0xFF666666),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
                }

                // 식단 목록
                items(state.diets) { diet ->
                    DietCard(diet = diet)
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
private fun WeeklyDietHeader(
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
private fun DietCard(diet: DietDay) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 음식 이미지
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(diet.imgUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = diet.food,
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = android.R.drawable.ic_menu_gallery),
                error = painterResource(id = android.R.drawable.ic_menu_gallery)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = diet.food,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )

                    // 주중 일자 표시
                    Text(
                        text = "${diet.dayInWeek}일차",
                        fontSize = 12.sp,
                        color = Color.White,
                        modifier = Modifier
                            .background(
                                Color(0xFF4CAF50),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = diet.detail,
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 임신 일수 표시
                Text(
                    text = "임신 ${diet.dayNo}일째",
                    fontSize = 12.sp,
                    color = Color(0xFF999999),
                    modifier = Modifier
                        .background(
                            Color(0xFFF5F5F5),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}