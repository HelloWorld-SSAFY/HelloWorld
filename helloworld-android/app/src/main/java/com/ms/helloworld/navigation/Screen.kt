package com.ms.helloworld.navigation

// 화면 라우트 관리
sealed class Screen(val route: String) {
    object HomeScreen : Screen("home")
    object DiaryScreen : Screen("diary")
    object DiaryDetailScreen : Screen("diary_detail") {
        fun createRoute(day: Int): String {
            return "diary_detail/$day"
        }
    }
    object RecommendScreen : Screen("recommend")
    object CalendarScreen : Screen("calendar") {
        fun createRoute(selectedDate: String? = null): String {
            return if (selectedDate != null) {
                "calendar?selectedDate=$selectedDate"
            } else {
                "calendar"
            }
        }
    }
    object LoginScreen : Screen("login")
    object OnboardingScreens : Screen("onboarding")
    object CoupleProfileScreen: Screen("profile")
    object RecordDetailScreen : Screen("record_detail")
}