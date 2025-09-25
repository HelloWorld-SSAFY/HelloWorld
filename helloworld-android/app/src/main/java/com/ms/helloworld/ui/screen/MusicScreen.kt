package com.ms.helloworld.ui.screen

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ms.helloworld.dto.response.MusicDelivery
import com.ms.helloworld.ui.components.CustomTopAppBar
import com.ms.helloworld.ui.theme.MainColor
import com.ms.helloworld.viewmodel.MusicViewModel

@Composable
private fun EmptyContent() {
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
                text = "🎵",
                fontSize = 48.sp
            )
            Text(
                text = "아직 추천할 음악이 없어요",
                color = Color.Black.copy(alpha = 0.8f),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "조금 더 기다려주시면\n맞춤 음악을 추천해드릴게요",
                color = Color.Black.copy(alpha = 0.6f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun MusicScreen(
    navController: NavHostController,
    viewModel: MusicViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    when {
        uiState.isLoading -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFF8F9FA),
                                Color(0xFFE9ECEF)
                            )
                        )
                    )
            ) {
                CustomTopAppBar(
                    title = "오늘의 음악",
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
                                Color(0xFFF8F9FA),
                                Color(0xFFE9ECEF)
                            )
                        )
                    )
            ) {
                CustomTopAppBar(
                    title = "오늘의 음악",
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
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFF8F9FA),
                                Color(0xFFE9ECEF)
                            )
                        )
                    )
            ) {
                CustomTopAppBar(
                    title = "오늘의 음악",
                    navController = navController
                )

                if (uiState.musics.isEmpty()) {
                    EmptyContent()
                } else {
                    FullScreenMusicContent(
                        navController = navController,
                        musics = uiState.musics,
                        onMusicClick = { music -> uriHandler.openUri(music.url) }
                    )
                }
            }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = MainColor,
                strokeWidth = 3.dp
            )
            Text(
                text = "음악 콘텐츠를 불러오는 중...",
                color = Color.Black.copy(alpha = 0.7f),
                fontSize = 16.sp
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
                text = "오류가 발생했습니다",
                color = Color.Black,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = error,
                color = Color.Black.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MainColor
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("다시 시도")
            }
        }
    }
}

@Composable
private fun FullScreenMusicContent(
    navController: NavHostController,
    musics: List<MusicDelivery>,
    onMusicClick: (MusicDelivery) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items(musics.size) { index ->
            FullScreenMusicCard(
                music = musics[index],
                onClick = { onMusicClick(musics[index]) },
            )
        }
    }
}

@Composable
private fun FullScreenMusicCard(
    music: MusicDelivery,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .height(600.dp)
            .clickable { onClick() }
    ) {
        // 배경 이미지
        AsyncImage(
            model = music.thumbnail,
            contentDescription = music.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        // 그라데이션 오버레이
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.8f)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
                .clip(RoundedCornerShape(16.dp))
        )

        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            Spacer(modifier = Modifier.weight(1f))

            // 하단 콘텐츠 정보
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.2f))
                    .padding(18.dp)
            ) {
                // 제목
                Text(
                    text = music.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 30.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 제공자 정보
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 제공자 아바타
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = music.provider.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column {
                        Text(
                            text = music.provider,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "추천도 ${(music.score * 100).toInt()}%",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )

                            // 음악 길이 표시 (duration_sec이 있는 경우)
                            music.duration_sec?.let { duration ->
                                Text(
                                    text = "•",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp
                                )
                                val minutes = duration / 60
                                val seconds = duration % 60
                                Text(
                                    text = "${minutes}:${seconds.toString().padStart(2, '0')}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    // 가운데 Spacer로 오른쪽 끝으로 밀기
                    Spacer(modifier = Modifier.weight(1f))

                    // 원형 액션 버튼
                    Box(
                        modifier = Modifier.background(Color.White, shape = CircleShape)
                    ) {
                        OutlinedButton(
                            onClick = onClick,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            border = BorderStroke(0.dp, Color.Transparent),
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "재생",
                                tint = Color.Black,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}