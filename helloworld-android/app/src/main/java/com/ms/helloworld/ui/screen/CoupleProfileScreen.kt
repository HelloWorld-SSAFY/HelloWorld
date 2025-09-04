package com.ms.helloworld.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
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
    var showInviteCodeBottomSheet by remember { mutableStateOf(false) }
    var inviteCode by remember { mutableStateOf("") }
    val bottomSheetState = rememberModalBottomSheetState()
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
                        InviteCodeButton(
                            onClick = {
                                // 초대코드 생성
                                inviteCode = generateInviteCode()
                                showInviteCodeBottomSheet = true
                            }
                        )
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
    
    // 초대코드 바텀시트
    if (showInviteCodeBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showInviteCodeBottomSheet = false },
            sheetState = bottomSheetState
        ) {
            InviteCodeBottomSheetContent(
                inviteCode = inviteCode,
                onDismiss = { showInviteCodeBottomSheet = false }
            )
        }
    }
}

@Composable
private fun InviteCodeButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF0C7B33)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = "초대코드 생성",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

@Composable
private fun InviteCodeBottomSheetContent(
    inviteCode: String,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "초대코드",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFF5F5F5)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = inviteCode,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0C7B33),
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(
                    onClick = {
                        // TODO: 클립보드에 복사 기능 구현
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "공유",
                        tint = Color(0xFF0C7B33)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "이 코드를 파트너에게 공유해 주세요",
            fontSize = 16.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0C7B33)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "확인",
                fontSize = 16.sp,
                color = Color.White
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun generateInviteCode(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return (1..6)
        .map { chars.random() }
        .joinToString("")
}

@Composable
private fun MenuItemText(text: String) {
    Text(
        text = text,
        fontSize = 20.sp,
        color = Color.Black,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Preview(showBackground = true)
@Composable
fun CoupleScreenPreview() {
    CoupleProfileScreen(navController = null as NavHostController)
}