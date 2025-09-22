package com.ms.helloworld.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ms.helloworld.R
import com.ms.helloworld.navigation.Screen
import com.ms.helloworld.viewmodel.SplashViewModel

@Composable
fun SplashScreen(
    navController: NavHostController,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // 스플래시 시작 시 자동 로그인 체크
    LaunchedEffect(Unit) {
        viewModel.autoLogin(context)
    }

    // UI 상태에 따른 네비게이션 처리
    LaunchedEffect(uiState) {
        when (uiState) {
            is SplashViewModel.UiState.GoHome -> {
                navController.navigate(Screen.HomeScreen.route) {
                    popUpTo(Screen.SplashScreen.route) { inclusive = true }
                }
            }
            is SplashViewModel.UiState.GoOnboarding -> {
                navController.navigate(Screen.OnboardingScreens.route) {
                    popUpTo(Screen.SplashScreen.route) { inclusive = true }
                }
            }
            is SplashViewModel.UiState.GoLogin -> {
                navController.navigate(Screen.LoginScreen.route) {
                    popUpTo(Screen.SplashScreen.route) { inclusive = true }
                }
            }
            is SplashViewModel.UiState.Loading -> {
                // 로딩 중에는 아무것도 하지 않음 (스플래시 화면 유지)
            }
        }
    }

    // 스플래시 UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0XFFF3EDDE)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.helloworld_logo),
            contentDescription = "HelloWorld Logo",
            modifier = Modifier
                .fillMaxWidth(0.42f)
                .aspectRatio(1f)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    }
}