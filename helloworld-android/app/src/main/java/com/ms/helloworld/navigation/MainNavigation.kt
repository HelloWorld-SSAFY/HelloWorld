package com.ms.helloworld.navigation

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.ms.helloworld.MainActivity
import com.ms.helloworld.ui.screen.CalendarScreen
import com.ms.helloworld.ui.screen.CoupleProfileScreen
import com.ms.helloworld.ui.screen.HomeScreen
import com.ms.helloworld.ui.screen.LoginScreen
import com.ms.helloworld.ui.screen.OnboardingScreens

@Composable
fun MainNavigation(
    navController: NavHostController = rememberNavController()
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

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
            startDestination = Screen.LoginScreen.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable(Screen.LoginScreen.route) {
                LoginScreen(navController)
            }

            composable(Screen.HomeScreen.route) {
                HomeScreen(navController)
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
        }
    }
}