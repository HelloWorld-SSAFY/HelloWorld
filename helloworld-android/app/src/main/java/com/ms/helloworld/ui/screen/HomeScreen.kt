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
import com.ms.helloworld.ui.components.*
import com.ms.helloworld.dto.response.CalendarPost
import com.ms.helloworld.dto.response.MomProfile
import com.ms.helloworld.viewmodel.HomeViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavHostController
import com.ms.helloworld.navigation.Screen
import java.time.LocalDate

@SuppressLint("NewApi")
@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = viewModel()
) {
    val backgroundColor = Color(0xFFFFFFFF)
    var posts by remember { mutableStateOf(mapOf<String, List<CalendarPost>>()) }

    val momProfile by viewModel.momProfile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()


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
            if (isLoading) {
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
            } else {
                ProfileSection(
                    momProfile = momProfile,
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
                postsMap = posts
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