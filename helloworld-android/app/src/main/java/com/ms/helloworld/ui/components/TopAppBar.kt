package com.ms.helloworld.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsEndWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.packInts
import androidx.navigation.NavHostController
import com.ms.helloworld.R
import com.ms.helloworld.navigation.Screen
import com.ms.helloworld.ui.theme.MainColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTopAppBar(
    title: String,
    navController: NavHostController,
){
    TopAppBar(
        title = {
            when (title) {
                "home" -> {
                    Icon(
                        painter = painterResource(R.drawable.ic_hello_world_topappbar),
                        contentDescription = "Hello World Logo",
                        tint = Color.Unspecified,
                        modifier = Modifier
                            .height(150.dp)
                            .fillMaxWidth()
                            .padding(start = 24.dp)
                    )
                }
                "profile" -> {
                    Text(
                        text = "프로필",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 40.dp)
                    )
                }
                "wearable" -> {
                    Text(
                        text = "웨어러블 기반 추천",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 16.dp)
                    )
                }
                "출산일기", "관찰일기", "임신 통계", "오늘의 명상", "오늘의 요가", "오늘의 음악", "오늘의 장소" -> {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 40.dp)
                    )
                }
                "알림" -> {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 40.dp)
                    )
                }
                "calendar" -> {
                    Text(
                        text = "캘린더",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 40.dp)
                    )
                }
                "diary" -> {}
                else -> {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 16.dp)
                    )
                }
            }
        },
        navigationIcon = {
            when(title) {
                "profile", "calendar", "diary", "출산일기", "관찰일기", "임신 통계", "알림", "오늘의 명상", "오늘의 요가", "오늘의 음악", "오늘의 장소" -> {
                    Icon(
                        painter = painterResource(R.drawable.ic_back),
                        contentDescription = "뒤로가기",
                        tint = Color.Unspecified,
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .clickable{
                                navController.popBackStack()
                            }
                    )
                }
            }
        },
        actions = {
            when (title) {
                "home" -> {
                    Icon(
                        painter = painterResource(R.drawable.property_2_bell),
                        contentDescription = "알림",
                        tint = Color.Unspecified,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clickable{
                                navController.navigate(Screen.NotificationScreen.route)
                            }
                    )
                }
            }
        }
    )
}