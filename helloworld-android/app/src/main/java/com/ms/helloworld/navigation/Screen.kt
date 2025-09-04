package com.ms.helloworld.navigation

// 화면 라우트 관리
sealed class Screen(val route: String) {
    object HomeScreen : Screen("home")
    object DiaryScreen : Screen("diary")
    object RecommendScreen : Screen("recommend")
    object LoginScreen : Screen("login")
    object OnboardingScreens : Screen("onboarding")
    object CoupleProfileScreen: Screen("profile")
}