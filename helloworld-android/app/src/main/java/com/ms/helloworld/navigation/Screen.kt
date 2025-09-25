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
    object DiaryRegisterScreen : Screen("diary_register") {
        fun createRoute(
            diaryType: String, // "birth" | "observation"
            day: Int,
            isEdit: Boolean = false
        ): String = "diary_register/$diaryType/$day/$isEdit"
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
    object HealthStatusScreen : Screen("health_status")
    object HealthRegisterScreen : Screen("health_register")
    object DiaryBoardScreen : Screen("diary_board") {
        fun createRoute(diaryType: String, day: Int): String {
            return "diary_board/$diaryType/$day"
        }
    }
    object NotificationScreen : Screen("notification")
    object WearableRecommendedScreen : Screen("wearable_recommended")
    object SplashScreen : Screen("splash")
    object MusicScreen : Screen("music")
    object MeditationScreen : Screen("meditation")
    object YogaScreen : Screen("yoga")
}