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

    // 애니메이션을 한 번만 초기화하고 uiState 변화와 독립적으로 관리
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.splashlottie))

    // 애니메이션은 처음 한 번만 시작하고 계속 실행
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = true,
        iterations = LottieConstants.IterateForever,
        speed = 0.2f,
        cancellationBehavior = LottieCancellationBehavior.OnIterationFinish
    )

    // 스플래시 시작 시 자동 로그인 체크
    LaunchedEffect(Unit) {
        viewModel.autoLogin(context)
    }

    // UI 상태에 따른 네비게이션 처리
    LaunchedEffect(uiState) {
        when (uiState) {
            is SplashViewModel.UiState.GoHome -> {
                navController.navigate(Screen.HomeScreen.route) {
                    popUpTo(Screen.SplashScreen.route) { inclusive = true } // 전체 백스택 클리어
                    launchSingleTop = true
                }
            }

            is SplashViewModel.UiState.GoOnboarding -> {
                navController.navigate(Screen.OnboardingScreens.route) {
                    popUpTo(Screen.SplashScreen.route) { inclusive = true } // 전체 백스택 클리어
                    launchSingleTop = true
                }
            }

            is SplashViewModel.UiState.GoLogin -> {
                navController.navigate(Screen.LoginScreen.route) {
                    popUpTo(Screen.SplashScreen.route) { inclusive = true } // 전체 백스택 클리어
                    launchSingleTop = true
                }
            }

            is SplashViewModel.UiState.Loading -> {
                // 로딩 중에는 아무것도 하지 않음 (스플래시 화면 유지)
            }
        }
    }

    // 스플래시 화면 - uiState와 관계없이 항상 표시
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0XFFF3EDDE)),
        contentAlignment = Alignment.Center
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.size(400.dp)
        )
    }
}