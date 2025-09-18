package com.ms.helloworld.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ms.helloworld.ui.components.CustomTopAppBar
import com.ms.helloworld.ui.components.ProfileEditDialog
import com.ms.helloworld.viewmodel.CoupleProfileViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoupleProfileScreen(
    navController: NavHostController,
    onBackClick: () -> Unit = {},
    viewModel: CoupleProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showInviteCodeBottomSheet by remember { mutableStateOf(false) }
    var showProfileEditDialog by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState()
    val backgroundColor = Color(0xFFFFFFFF)

    val isPartnerConnected = false // TODO: 파트너 연결 상태 백엔드에서 관리 필요
    val shouldShowInviteCode = true // TODO: 성별 정보 범위에서 처리 필요

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            CustomTopAppBar(
                title = "profile",
                navController = navController
            )
            // 커플 프로필 섹션
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 8.dp)
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    // 아내 프로필 (왼쪽)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(
                                    Color(0xFFA8D5A8),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "사진",
                                color = Color.Black,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))


                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = state.momProfile?.nickname ?: "아내 닉네임",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.Black,
                                textAlign = TextAlign.Center
                            )

                            IconButton(
                                onClick = { showProfileEditDialog = true },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "아내 프로필 수정",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(30.dp))
                    
                    // 남편 프로필 (오른쪽)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(
                                    Color(0xFFB5D3F7),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "사진",
                                color = Color.Black,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                                                
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "남편 닉네임", // TODO: 파트너 정보 API에서 가져오기
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.Black,
                                textAlign = TextAlign.Center
                            )

                            // TODO: 성별에 따른 수정 버튼 처리
                        }
                    }
                }
            }

            // 공통 임신 정보 섹션
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 임신 일수 (첨번째 줄)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "임신 일수",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = state.momProfile?.let { profile ->
                            "${profile.currentDay}일 (${profile.pregnancyWeek}주)"
                        } ?: "정보 없음",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        textAlign = TextAlign.End
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 출산예정일 (두번째 줄)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "출산예정일",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = state.momProfile?.dueDate?.format(
                            DateTimeFormatter.ofPattern("yyyy.MM.dd")
                        ) ?: "정보 없음",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        textAlign = TextAlign.End
                    )
                }
            }


            HorizontalDivider(
                thickness = 1.dp,
                color = Color.LightGray
            )

            // 설정 섹션
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "계정 설정",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (shouldShowInviteCode) {
                    PartnerConnectionButton(
                        isPartnerConnected = isPartnerConnected,
                        onInviteCodeClick = {
                            showInviteCodeBottomSheet = true
                        },
                        onDisconnectClick = {
                            // TODO: 연동 해제 처리
                        }
                    )
                }
                

                Spacer(modifier = Modifier.height(16.dp))
                MenuItemWithArrow("로그아웃") {
                    // TODO: 로그아웃 처리
                }
                Spacer(modifier = Modifier.height(16.dp))
                MenuItemWithArrow("회원탈퇴") {
                    // TODO: 회원탈퇴 처리
                }
            }
        }
    
    // 초대코드 바텀시트
    if (showInviteCodeBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showInviteCodeBottomSheet = false },
            sheetState = bottomSheetState,
            containerColor = Color(0xFFFFFFFF)
        ) {
            InviteCodeBottomSheetContent(
                inviteCode = state.inviteCode ?: generateInviteCode(),
                onDismiss = { showInviteCodeBottomSheet = false }
            )
        }
    }

    // 프로필 수정 다이얼로그
    if (showProfileEditDialog) {
        val memberProfile = state.memberProfile
        ProfileEditDialog(
            currentNickname = state.momProfile?.nickname ?: "",
            currentDueDate = state.momProfile?.dueDate,
            currentAge = memberProfile?.age,
            currentMenstrualDate = memberProfile?.menstrualDate?.let { LocalDate.parse(it) },
            currentGender = memberProfile?.gender,
            onDismiss = { showProfileEditDialog = false },
            onSave = { nickname, age, menstrualDate, dueDate ->
                viewModel.updateProfile(nickname, age, menstrualDate, dueDate)
                showProfileEditDialog = false
            }
        )
    }
}

@Composable
private fun PartnerConnectionButton(
    isPartnerConnected: Boolean,
    onInviteCodeClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isPartnerConnected) "연동해제" else "초대코드",
            fontSize = 16.sp,
            color = Color.Black
        )
        
        Button(
            onClick = if (isPartnerConnected) onDisconnectClick else onInviteCodeClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF49699)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Text(
                text = if (isPartnerConnected) "해제하기" else "공유하기",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

@Composable
private fun MenuItemWithArrow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            color = Color.Black
        )
        
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(20.dp)
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
                containerColor = Color(0xFFFFFFFF)
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
                    color = Color(0xFFF49699),
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
                        tint = Color(0xFFF49699)
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
                containerColor = Color(0xFFF49699)
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


@Preview(showBackground = true)
@Composable
fun CoupleScreenPreview() {
    CoupleProfileScreen(navController = null as NavHostController)
}