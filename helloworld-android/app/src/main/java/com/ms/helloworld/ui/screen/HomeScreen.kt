package com.ms.helloworld.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.ms.helloworld.dto.response.CalendarPost
import com.ms.helloworld.dto.response.MomProfile
import com.ms.helloworld.viewmodel.HomeViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavHostController
import com.ms.helloworld.navigation.Screen
import java.time.LocalDate

@SuppressLint("NewApi")
@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val backgroundColor = Color(0xFFFFFFFF)

    val momProfile by viewModel.momProfile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val calendarEvents by viewModel.calendarEvents.collectAsState()

    // 캘린더 이벤트 변경 감지
    LaunchedEffect(calendarEvents) {
        val totalEvents = calendarEvents.values.sumOf { it.size }
        calendarEvents.forEach { (date, events) ->
            println("🏠 $date: ${events.size}개 이벤트")
        }
    }

    // 초기 로드는 별도 처리 (로딩 상태 표시)
    // 이후 새로고침은 silent refresh 사용

    // Lifecycle 이벤트 감지하여 화면 복귀 시 동기화
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                println("🏠 HomeScreen - 화면 복귀, 프로필과 캘린더 이벤트 새로고침")
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
                .padding(20.dp)
        ) {
            if (isLoading && momProfile == null) {
                // 초기 로딩일 때만 로딩 상태 표시
                ProfileSection(
                    momProfile = MomProfile(
                        nickname = "로딩중...",
                        pregnancyWeek = 1,
                        dueDate = LocalDate.now()
                    ),
                    onClick = {
                        navController.navigate(Screen.CoupleProfileScreen.route)
                    }
                )
            } else if (momProfile != null) {
                // 데이터가 있으면 항상 표시 (백그라운드 새로고침 중에도)
                ProfileSection(
                    momProfile = momProfile,
                    onClick = {
                        navController.navigate(Screen.CoupleProfileScreen.route)
                    }
                )
            } else {
                // 데이터가 없고 로딩도 아닌 경우 (에러 상태)
                ProfileSection(
                    momProfile = MomProfile(
                        nickname = "정보 없음",
                        pregnancyWeek = 1,
                        dueDate = LocalDate.now()
                    ),
                    onClick = {
                        navController.navigate(Screen.CoupleProfileScreen.route)
                    }
                )
            }
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = Color.LightGray
        )

        // 캘린더 섹션
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            CalendarSection(
                onDateClick = { dateKey ->
                    navController.navigate(Screen.CalendarScreen.createRoute(dateKey)) {
                        launchSingleTop = true
                    }
                },
                postsMap = calendarEvents
            )
        }

        // 오늘의 추천 섹션
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "오늘의 추천",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(16.dp))

            TodayRecommendationSection()
        }

        // 건강 상태 섹션
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "건강 상태",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(16.dp))

            HealthStatusSection()
        }
    }
}


@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    HomeScreen(navController = null as NavHostController)
}