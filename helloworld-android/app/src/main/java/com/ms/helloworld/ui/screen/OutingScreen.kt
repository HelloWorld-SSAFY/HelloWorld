package com.ms.helloworld.ui.screen

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ms.helloworld.R
import com.ms.helloworld.dto.response.OutingDelivery
import com.ms.helloworld.ui.components.CustomTopAppBar
import com.ms.helloworld.ui.theme.MainColor
import com.ms.helloworld.viewmodel.OutingViewModel

@Composable
fun OutingScreen(
    navController: NavHostController,
    viewModel: OutingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.isLoading -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFFF0F5), // 연한 핑크
                                Color(0xFFF0F8FF)  // 연한 하늘색
                            )
                        )
                    )
            ) {
                CustomTopAppBar(
                    title = "오늘의 장소",
                    navController = navController
                )
                LoadingContent()
            }
        }

        uiState.error != null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFFF0F5),
                                Color(0xFFF0F8FF)
                            )
                        )
                    )
            ) {
                CustomTopAppBar(
                    title = "오늘의 장소",
                    navController = navController
                )
                ErrorContent(
                    error = uiState.error!!,
                    onRetry = viewModel::retryLoading
                )
            }
        }

        else -> {
            Column(
                Modifier
                    .fillMaxSize()

            ) {
                CustomTopAppBar(
                    title = "오늘의 장소",
                    navController = navController
                )

                if (uiState.outings.isEmpty()) {
                    EmptyContent()
                } else {
                    OutingContent(
                        outings = uiState.outings,
                        onPlaceClick = { delivery ->
                            // 장소 클릭 시 처리 로직
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OutingContent(
    outings: List<OutingDelivery>,
    onPlaceClick: (OutingDelivery) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 따뜻한 인사 메시지
        item {
            WelcomeMessage(count = outings.size)
        }

        // 장소 카드들
        items(outings) { delivery ->
            CuteePlaceCard(
                delivery = delivery,
                onClick = { onPlaceClick(delivery) }
            )
        }
    }
}

@Composable
private fun WelcomeMessage(count: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFE4E1).copy(alpha = 0.8f) // 미스티 로즈
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "오늘 아기와 함께 가면 좋을\n${count}곳의 특별한 장소를 준비했어요 \uD83D\uDC95",
                fontSize = 16.sp,
                color = Color(0xFF6B4C93),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun CuteePlaceCard(
    delivery: OutingDelivery,
    onClick: () -> Unit
) {
    val cuteMessages = listOf(
        "아기와 함께 산책하기 좋아요",
        "마음이 편안해지는 곳이에요",
        "예쁜 추억을 만들어보세요",
        "여유로운 시간을 보내세요",
        "힐링이 필요할 때 추천해요",
        "기분 좋은 하루가 될 거예요 ",
        "아기에게도 좋은 경험이 될 거예요",
        "잠깐의 휴식이 필요할 때 ️"
    )

    val randomMessage = remember { cuteMessages.random() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // 순위와 하트 아이콘
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = Color(0xFFFFB6C1) // 라이트 핑크
                ) {
                    Text(
                        text = "${delivery.rank}번째 추천",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Icon(
                    painter = painterResource(R.drawable.ic_map),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 장소 이름 (메인 콘텐츠)
            Text(
                text = delivery.title ?: "특별한 장소",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4A4A4A),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 28.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 귀여운 메시지
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = randomMessage,
                    color = Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp
                )

            }

            Spacer(modifier = Modifier.height(12.dp))

            // 부드러운 구분선
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFFDDA0DD).copy(alpha = 0.3f), // 플럼
                                Color.Transparent
                            )
                        )
                    )
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            CircularProgressIndicator(
                color = Color(0xFFFFB6C1), // 라이트 핑크
                strokeWidth = 4.dp,
                modifier = Modifier.size(50.dp)
            )

            Text(
                text = "특별한 장소를 찾고 있어요",
                color = MainColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "아기와 함께 가면 좋을 곳들을\n정성스럽게 준비하고 있어요 💕",
                color = MainColor,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "💝 잠깐 문제가 생겼어요",
                color = MainColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "걱정하지 마세요!\n다시 시도해보면 좋은 장소들을 찾을 수 있어요",
                color = MainColor,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFB6C1)
                ),
                shape = RoundedCornerShape(25.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "다시 찾아보기",
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            Text(
                text = "🌸",
                fontSize = 60.sp
            )

            Text(
                text = "아직 준비된 장소가 없어요",
                color = MainColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "조금만 기다려주세요!\n예비맘을 위한 특별한 장소들을\n정성스럽게 준비하고 있어요 💜",
                color = MainColor,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}