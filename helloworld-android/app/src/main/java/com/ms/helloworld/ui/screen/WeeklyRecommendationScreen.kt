package com.ms.helloworld.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ms.helloworld.dto.response.DietDay
import com.ms.helloworld.dto.response.WorkoutItem
import com.ms.helloworld.dto.response.WorkoutType
import com.ms.helloworld.viewmodel.WeeklyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyRecommendationScreen(
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
    ) {
        // 상단 헤더
        WeeklyHeader(
            currentWeek = state.currentWeek,
            onBackClick = onBackClick,
            onPreviousWeek = { viewModel.changeWeek(state.currentWeek - 1) },
            onNextWeek = { viewModel.changeWeek(state.currentWeek + 1) }
        )

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                // 음식 추천
                if (state.diets.isNotEmpty()) {
                    FoodRecommendationSection(diets = state.diets)
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // 운동 추천들
                state.workouts.forEach { workout ->
                    WorkoutRecommendationCard(workout = workout)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 주차 정보
                state.weeklyInfo?.let { info ->
                    InfoCard(
                        title = "${state.currentWeek}주차 정보",
                        description = info,
                        icon = "📖"
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }

    // 에러 처리
    state.errorMessage?.let { error ->
        LaunchedEffect(error) {
            // 에러 스낵바 표시 로직
            viewModel.clearError()
        }
    }
}

@Composable
private fun WeeklyHeader(
    currentWeek: Int,
    onBackClick: () -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "뒤로가기",
                tint = Color(0xFF333333)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPreviousWeek,
                enabled = currentWeek > 1
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowLeft,
                    contentDescription = "이전 주차",
                    tint = if (currentWeek > 1) Color(0xFF333333) else Color(0xFFCCCCCC)
                )
            }

            Text(
                text = "${currentWeek}주차",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333),
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            IconButton(
                onClick = onNextWeek,
                enabled = currentWeek < 42
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "다음 주차",
                    tint = if (currentWeek < 42) Color(0xFF333333) else Color(0xFFCCCCCC)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FoodRecommendationSection(diets: List<DietDay>) {
    Column {
        Text(
            text = "음식 추천",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(diets.take(4)) { diet ->
                FoodRecommendationCard(diet = diet)
            }
        }
    }
}

@Composable
private fun FoodRecommendationCard(diet: DietDay) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(diet.imgUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = diet.food,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = android.R.drawable.ic_menu_gallery),
                error = painterResource(id = android.R.drawable.ic_menu_gallery)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = diet.food,
            fontSize = 12.sp,
            color = Color(0xFF666666),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun WorkoutRecommendationCard(workout: WorkoutItem) {
    when (workout.type) {
        WorkoutType.TEXT -> {
            workout.text?.let { text ->
                InfoCard(
                    title = "스트레칭 추천",
                    description = text,
                    icon = "🧘‍♀️"
                )
            }
        }
        WorkoutType.VIDEO -> {
            workout.title?.let { title ->
                InfoCard(
                    title = "운동 영상",
                    description = title,
                    icon = "📹"
                )
            }
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    description: String,
    icon: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = icon,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                fontSize = 14.sp,
                color = Color(0xFF666666),
                lineHeight = 20.sp
            )
        }
    }
}