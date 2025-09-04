package com.ms.helloworld.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoupleProfileScreen(
    navController: NavHostController,
    onBackClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // Top App Bar
        TopAppBar(
            title = { },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "뒤로가기"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        // Main Content with overlapping profiles
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // Main Card (positioned to allow profile overlap)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(top = 50.dp), // Space for half of profile circles
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF8DC)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 60.dp, start = 28.dp, end = 28.dp, bottom = 28.dp), // Top padding for overlapping profiles
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "산모 닉네임",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Info Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text(
                            text = "일수 (몇주차)",
                            fontSize = 16.sp,
                            color = Color.Black
                        )

                        Text(
                            text = "출산예정일",
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    // Menu Items
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MenuItemText("초대코드")
                        Spacer(modifier = Modifier.height(32.dp))
                        MenuItemText("설정")
                        Spacer(modifier = Modifier.height(32.dp))
                        MenuItemText("로그아웃")
                        Spacer(modifier = Modifier.height(32.dp))
                        MenuItemText("회원탈퇴")
                    }
                }
            }

            // Profile Circles (overlapping the card top)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp) // Position so they overlap card
                    .zIndex(1f), // Ensure they appear on top
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Profile Circle
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            Color(0xFFA8D5A8),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "신모 프로필",
                        color = Color.Black,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // Right Profile Circle
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            Color(0xFFA8D5A8),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "남편 프로필",
                        color = Color.Black,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuItemText(text: String) {
    Text(
        text = text,
        fontSize = 20.sp,
        color = Color.Black,
        modifier = Modifier.padding(vertical = 6.dp),


    )
}

@Preview(showBackground = true)
@Composable
fun CoupleScreenPreview() {
    CoupleProfileScreen(navController = null as NavHostController)
}