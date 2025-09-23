package com.ms.helloworld.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.airbnb.lottie.compose.*
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

    // 네비게이션 상태 관리 (한 번만 실행되도록)
    var hasNavigated by remember { mutableStateOf(false) }


    // 스플래시 시작 시 자동 로그인 체크
    LaunchedEffect(Unit) {
        viewModel.autoLogin(context)
    }

    // UI 상태에 따른 네비게이션 처리
    LaunchedEffect(uiState) {
        // 이미 네비게이션했으면 중복 실행 방지
        if (hasNavigated) return@LaunchedEffect

        when (uiState) {
            is SplashViewModel.UiState.GoHome -> {
                hasNavigated = true
                navController.navigate(Screen.HomeScreen.route) {
                    popUpTo(0) { inclusive = true } // 전체 백스택 클리어
                    launchSingleTop = true
                }
            }
            is SplashViewModel.UiState.GoOnboarding -> {
                hasNavigated = true
                navController.navigate(Screen.OnboardingScreens.route) {
                    popUpTo(0) { inclusive = true } // 전체 백스택 클리어
                    launchSingleTop = true
                }
            }
            is SplashViewModel.UiState.GoLogin -> {
                hasNavigated = true
                navController.navigate(Screen.LoginScreen.route) {
                    popUpTo(0) { inclusive = true } // 전체 백스택 클리어
                    launchSingleTop = true
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
        // Lottie 애니메이션 사용
        val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.splashlottie))

        var isPlaying by remember { mutableStateOf(true) }

        val progress by animateLottieCompositionAsState(
            composition = composition,
            isPlaying = isPlaying,
            iterations = LottieConstants.IterateForever,
            speed = 0.2f,
            cancellationBehavior = LottieCancellationBehavior.OnIterationFinish
        )

        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier
                .size(400.dp)

        )
    }
}