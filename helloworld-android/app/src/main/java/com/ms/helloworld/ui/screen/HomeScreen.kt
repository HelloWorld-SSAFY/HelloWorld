package com.ms.helloworld.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ms.helloworld.ui.components.*
import com.ms.helloworld.dto.response.MomProfile
import com.ms.helloworld.viewmodel.HomeViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavHostController
import com.ms.helloworld.R
import com.ms.helloworld.navigation.Screen
import com.ms.helloworld.repository.StepsRepository
import com.ms.helloworld.ui.theme.MainColor
import com.ms.helloworld.util.LocationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

@SuppressLint("NewApi")
@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val momProfile by viewModel.momProfile.collectAsState()
    val calendarEvents by viewModel.calendarEvents.collectAsState()
    val currentPregnancyDay by viewModel.currentPregnancyDay.collectAsState()

    // 테스트용 의존성들
    val stepsRepository: StepsRepository = viewModel.stepsRepository
    val locationManager: LocationManager = viewModel.locationManager

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
                        .padding(16.dp)
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

        // 캘린더 섹션
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.baby),
                contentDescription = "태아",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(
                thickness = 0.5.dp,
                color = Color(0xFFD0D0D0)
            )

            Spacer(modifier = Modifier.height(16.dp))

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
                onWeeklyRecommendationClick = {
                    // 현재 임신 주차를 가져와서 WeeklyRecommendationScreen으로 이동
                    val currentWeek = ((currentPregnancyDay - 1) / 7) + 1
                    navController.navigate("weekly_recommendation/$currentWeek")
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "걸음수 API 테스트(개발용)",
                fontSize = 18.sp,
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            StepsTestButton(
                stepsRepository = stepsRepository,
                locationManager = locationManager
            )
        }
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
                            val result = stepsRepository.submitStepsData(location.first, location.second)
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