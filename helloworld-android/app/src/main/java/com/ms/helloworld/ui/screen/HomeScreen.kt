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
    val momProfile by viewModel.momProfile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val calendarEvents by viewModel.calendarEvents.collectAsState()
    val currentPregnancyDay by viewModel.currentPregnancyDay.collectAsState()

    // 앱 시작 시 초기 데이터 로딩
    LaunchedEffect(Unit) {
        // 데이터가 초기 상태이 강제 새로고침
        if (momProfile.nickname == "로딩중") {
            viewModel.forceRefreshProfile()
        } else {
            viewModel.refreshProfile()
        }

        // 캘린더 이벤트도 함께 로딩
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
                .padding(20.dp)
        ) {
            if (isLoading && momProfile == null) {
                // 초기 로딩일 때만 로딩 상태 표시
                ProfileSection(
                    momProfile = MomProfile(
                        nickname = "로딩중",
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
                    currentPregnancyDay = currentPregnancyDay,
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
            thickness = 0.5.dp,
            color = Color(0xFFD0D0D0)
        )

        // 캘린더 섹션
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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
                .padding(16.dp)
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