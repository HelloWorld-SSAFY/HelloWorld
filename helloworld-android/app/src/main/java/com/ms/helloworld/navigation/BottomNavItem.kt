package com.ms.helloworld.navigation

import androidx.annotation.DrawableRes
import com.ms.helloworld.R

sealed class BottomNavItem(
    val label: String,
    val route: String,
    @DrawableRes val iconRes: Int,
    @DrawableRes val selectedIconRes: Int
){
    object Home : BottomNavItem("홈", Screen.HomeScreen.route, R.drawable.ic_home, R.drawable.ic_home_selected)
    object Diary : BottomNavItem("출산일기", Screen.DiaryScreen.route, R.drawable.ic_home, R.drawable.ic_home_selected)
    object Recommend : BottomNavItem("추천", Screen.RecommendScreen.route, R.drawable.ic_home, R.drawable.ic_home_selected)

    companion object {
        val items = listOf(Home, Diary, Recommend)
    }
}