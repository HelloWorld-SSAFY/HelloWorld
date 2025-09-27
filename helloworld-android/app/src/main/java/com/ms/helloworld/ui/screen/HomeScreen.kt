package com.ms.helloworld.ui.screen

import android.annotation.SuppressLint
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieClipSpec
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.ms.helloworld.R
import com.ms.helloworld.navigation.Screen
import com.ms.helloworld.repository.StepsRepository
import com.ms.helloworld.ui.components.CalendarSection
import com.ms.helloworld.ui.components.CustomTopAppBar
import com.ms.helloworld.ui.components.ProfileSection
import com.ms.helloworld.ui.components.TodayRecommendationSection
import com.ms.helloworld.ui.theme.MainColor
import com.ms.helloworld.util.LocationManager
import com.ms.helloworld.viewmodel.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@SuppressLint("NewApi")
@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val momProfile by viewModel.momProfile.collectAsState()
    val calendarEvents by viewModel.calendarEvents.collectAsState()
    val currentPregnancyDay by viewModel.currentPregnancyDay.collectAsState()

    // 앱 시작 시 초기 데이터 로딩
    LaunchedEffect(Unit) {
        viewModel.forceRefreshProfile()
        viewModel.refreshProfile()
        viewModel.refreshCalendarEvents()
    }

    // Lifecycle 이벤트 감지하여 화면 복귀 시 동기화
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshProfile()
                viewModel.refreshCalendarEvents()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    // 점의 위치를 추적하기 위한 상태
    var dotPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val density = LocalDensity.current

    // Lottie 애니메이션 준비 (Box 레벨에서)
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.home_baby))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = true,
        iterations = LottieConstants.IterateForever,
        speed = 0.2f,
        clipSpec = LottieClipSpec.Frame(min = 0, max = 10),
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            CustomTopAppBar(
                title = "home",
                navController = navController
            )
            // 프로필 섹션
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                // 프로필 섹션 - 데이터가 없으면 로딩 표시
                if (momProfile != null) {
                    // 실제 데이터가 있을 때만 표시
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                    ) {
                        ProfileSection(
                            momProfile = momProfile,
                            currentPregnancyDay = currentPregnancyDay,
                            onClick = {
                                navController.navigate(Screen.CoupleProfileScreen.route)
                            }
                        )
                    }
                } else {
                    // 데이터가 없으면 로딩 스켈레톤 표시
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        ProfileLoadingSkeleton()
                    }
                }
            }


            // 공간만 차지하는 Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp) // 최소 공간만 차지
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFCE4EC), // 연한 분홍
                                Color(0xFFF8BBD9), // 조금 더 진한 분홍
                                Color(0xFFFFE0E6), // 아주 연한 핑크
                                Color.Transparent
                            ),
                            radius = 300f
                        )
                    )
            ) {
                FloatingElements()

                // 중앙에 점 표시 (참조점) - 위치 추적
                Text(
                    text = "•",
                    fontSize = 12.sp,
                    color = MainColor,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .onGloballyPositioned { coordinates ->
                            dotPosition = coordinates.positionInRoot()
                        }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(
                thickness = 0.5.dp,
                color = Color(0xFFD0D0D0)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 캘린더 섹션
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                CalendarSection(
                    onDateClick = { dateKey ->
                        navController.navigate(Screen.CalendarScreen.createRoute(dateKey)) {
                            launchSingleTop = true
                        }
                    },
                    postsMap = calendarEvents
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 오늘의 추천 섹션
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "주차별 추천",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(16.dp))

                TodayRecommendationSection(
                    onDietClick = {
                        // 현재 임신 주차를 가져와서 WeeklyDietScreen으로 이동
                        val currentWeek = ((currentPregnancyDay - 1) / 7) + 1
                        navController.navigate("weekly_diet/$currentWeek")
                    },
                    onWorkoutClick = {
                        // 현재 임신 주차를 가져와서 WeeklyWorkoutScreen으로 이동
                        val currentWeek = ((currentPregnancyDay - 1) / 7) + 1
                        navController.navigate("weekly_workout/$currentWeek")
                    },
                    onInfoClick = {
                        // 현재 임신 주차를 가져와서 WeeklyInfoScreen으로 이동
                        val currentWeek = ((currentPregnancyDay - 1) / 7) + 1
                        navController.navigate("weekly_info/$currentWeek")
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

        } // Column 끝

        // Lottie를 실제 빨간 점 위치에 정확히 배치
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier
                .width(300.dp)
                .height(300.dp)
                .offset(
                    x = with(density) { (dotPosition.x - 170.dp.toPx()).toDp() }, // 점 위치 - Lottie 폭의 절반
                    y = with(density) { (dotPosition.y - 140.dp.toPx()).toDp() }  // 점 위치 - Lottie 높이의 절반
                )
        )
    } // Box 끝
}

@Composable
fun FloatingElements() {
    val infiniteTransition = rememberInfiniteTransition()

    // 여러 개의 떠다니는 요소들
    repeat(5) { index ->
        val offsetY by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -30f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 2000 + (index * 200),
                    easing = EaseInOutSine
                ),
                repeatMode = RepeatMode.Reverse
            )
        )

        val offsetX by infiniteTransition.animateFloat(
            initialValue = -10f,
            targetValue = 10f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1500 + (index * 300),
                    easing = EaseInOutSine
                ),
                repeatMode = RepeatMode.Reverse
            )
        )

        Text(
            text = if (index % 2 == 0) "💕" else "⭐",
            fontSize = (8 + index * 2).sp,
            modifier = Modifier
                .offset(
                    x = (50 + index * 60).dp + offsetX.dp,
                    y = (20 + index * 25).dp + offsetY.dp
                )
                .alpha(0.6f)
        )
    }
}

// 프로필 로딩 스켈레톤 컴포저블
@Composable
fun ProfileLoadingSkeleton() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp), // ProfileSection과 비슷한 높이
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(40.dp),
            color = MainColor
        )
    }
}

// 테스트용 컴포저블 - 메인 화면에 추가
@Composable
fun StepsTestButton(
    stepsRepository: StepsRepository,
    locationManager: LocationManager
) {
    var isLoading by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<String?>(null) }

    Column {
        Button(
            onClick = {
                isLoading = true
                // 코루틴으로 API 호출
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val location = locationManager.getCurrentLocation()
                        if (location != null) {
                            val result =
                                stepsRepository.submitStepsData(location.first, location.second)
                            lastResult = if (result.isSuccess) {
                                "성공: ${System.currentTimeMillis()}"
                            } else {
                                "실패: ${result.exceptionOrNull()?.message}"
                            }
                        } else {
                            lastResult = "위치 정보를 가져올 수 없음"
                        }
                    } catch (e: Exception) {
                        lastResult = "오류: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading
        ) {
            if (isLoading) {
                Text("전송 중")
            } else {
                Text("걸음수 API 테스트 호출")
            }
        }

        lastResult?.let { result ->
            Text(
                text = result,
                style = MaterialTheme.typography.bodySmall,
                color = if (result.startsWith("성공")) Color.Green else Color.Red
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    HomeScreen(navController = null as NavHostController)
}