package com.ms.helloworld.navigation

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.ms.helloworld.viewmodel.HomeViewModel
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import com.ms.helloworld.ui.screen.CalendarScreen
import com.ms.helloworld.ui.screen.CoupleProfileScreen
import com.ms.helloworld.ui.screen.DiaryScreen
import com.ms.helloworld.ui.screen.DiaryDetailScreen
import com.ms.helloworld.ui.screen.DiaryRegisterScreen
import com.ms.helloworld.ui.screen.HomeScreen
import com.ms.helloworld.ui.screen.LoginScreen
import com.ms.helloworld.ui.screen.OnboardingScreens
import com.ms.helloworld.ui.screen.WearableRecommendedScreen
import com.ms.helloworld.ui.screen.RecordDetailScreen
import com.ms.helloworld.ui.screen.HealthStatusScreen
import com.ms.helloworld.ui.screen.HealthRegisterScreen
import com.ms.helloworld.ui.screen.DiaryBoardScreen
import com.ms.helloworld.ui.screen.NotificationScreen
import com.ms.helloworld.ui.screen.SplashScreen

@Composable
fun MainNavigation(
    navController: NavHostController = rememberNavController()
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // HomeViewModel을 가져와서 refresh 기능 사용
    val homeViewModel: HomeViewModel = hiltViewModel()

    // 바텀 네비게이션을 표시할 화면들
    val screensWithBottomNav = BottomNavItem.items.map { it.route }

//    LaunchedEffect(Unit) {
//        MainActivity.deepLinkIntents.collect { intent ->
//            handleDeepLinkFromIntent(intent, navController)
//        }
//    }

    Scaffold(
        bottomBar = {
            // 바텀 네비게이션이 필요한 화면에서만 표시
            if (currentRoute in screensWithBottomNav) {
                BottomNavigationBar(
                    modifier = Modifier.navigationBarsPadding(),
                    selectedRoute = currentRoute,
                    onItemSelected = { route ->
                        if (route != currentRoute) {
                            navController.navigate(route) {
                                popUpTo(BottomNavItem.Home.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->

        NavHost(
            navController = navController,
            startDestination = Screen.SplashScreen.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable(Screen.SplashScreen.route) {
                SplashScreen(navController)
            }

            composable(Screen.LoginScreen.route) {
                LoginScreen(navController)
            }

            composable(Screen.HomeScreen.route) {
                HomeScreen(navController)
            }

            composable(Screen.DiaryScreen.route) {
                DiaryScreen(navController, homeViewModel = homeViewModel)
            }

            composable(
                route = "diary_detail/{day}",
                arguments = listOf(navArgument("day") { 
                    type = NavType.IntType
                })
            ) { backStackEntry ->
                val day = backStackEntry.arguments?.getInt("day") ?: 1
                DiaryDetailScreen(navController, initialDay = day)
            }

            composable(
                route = "diary_register/{diaryType}/{day}/{isEdit}",
                arguments = listOf(
                    navArgument("diaryType") { type = NavType.StringType },
                    navArgument("day") { type = NavType.IntType },
                    navArgument("isEdit") { type = NavType.BoolType }
                )
            ) { backStackEntry ->
                val diaryType = backStackEntry.arguments?.getString("diaryType") ?: "birth"
                val day = backStackEntry.arguments?.getInt("day") ?: 1
                val isEdit = backStackEntry.arguments?.getBoolean("isEdit") ?: false

                DiaryRegisterScreen(
                    navController = navController,
                    diaryType = diaryType,
                    day = day,
                    isEdit = isEdit
                )
            }

            composable(Screen.RecommendScreen.route) {
                WearableRecommendedScreen(navController)
            }

            composable(Screen.OnboardingScreens.route) {
                OnboardingScreens(navController)
            }

            composable(Screen.CoupleProfileScreen.route) {
                CoupleProfileScreen(
                    navController = navController,
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.RecordDetailScreen.route) {
                RecordDetailScreen(navController)
            }

            composable(
                route = "calendar?selectedDate={selectedDate}",
                arguments = listOf(navArgument("selectedDate") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                val selectedDate = backStackEntry.arguments?.getString("selectedDate")
                CalendarScreen(navController, selectedDate)
            }

            composable(Screen.HealthStatusScreen.route) {
                HealthStatusScreen(navController)
            }

            composable(Screen.HealthRegisterScreen.route) {
                HealthRegisterScreen(navController)
            }

            composable(
                route = "diary_board/{diaryType}/{day}",
                arguments = listOf(
                    navArgument("diaryType") { type = NavType.StringType },
                    navArgument("day") { type = NavType.IntType }
                )
            ) { backStackEntry ->
                val diaryType = backStackEntry.arguments?.getString("diaryType") ?: "birth"
                val day = backStackEntry.arguments?.getInt("day") ?: 1

                DiaryBoardScreen(
                    navController = navController,
                    diaryType = diaryType,
                    day = day,
                )
            }

            composable(Screen.NotificationScreen.route) {
                NotificationScreen(navController)
            }

            composable(Screen.WearableRecommendedScreen.route){
                WearableRecommendedScreen(navController)
            }
        }
    }
}