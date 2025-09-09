package com.ms.helloworld.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavHostController
import com.ms.helloworld.navigation.Screen
import com.ms.helloworld.ui.components.CustomTopAppBar

@SuppressLint("NewApi")
@Composable
fun WearableRecommendedScreen(
    navController: NavHostController
) {
    val backgroundColor = Color(0xFFF5F5F5)

    Column(
        modifier = Modifier
            .fillMaxSize()

            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        CustomTopAppBar(
            title = "wearable",
            navController = navController
        )

        // 실시간 상태 섹션
        RealTimeStatusSection()

        // 건강 지표 섹션
        HealthMetricsSection()

        // 음악 추천 섹션
        MusicRecommendationSection()

        // 나들이 추천 섹션
        OutdoorRecommendationSection()

        // 태동/진통 기록 섹션
        RecordSection(navController)
    }
}

@Composable
fun RealTimeStatusSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
        ) {
            Text(
                text = "실시간 상태",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "스트레스 지수 : ",
                    fontSize = 20.sp,
                    color = Color.Gray
                )
                Text(
                    text = "높음",
                    fontSize = 20.sp,
                    color = Color.Red,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = "안정 필요",
                fontSize = 16.sp,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun HealthMetricsSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // 원형 차트 3개
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CircularProgressChart(
                    percentage = 87f,
                    label = "스트레스",
                    color = Color(0xFFFF6B6B)
                )
                CircularProgressChart(
                    percentage = 65f,
                    label = "컨디션",
                    color = Color(0xFF4DABF7)
                )
                CircularProgressChart(
                    percentage = 26f,
                    label = "활동량",
                    color = Color(0xFFFFD93D)
                )
            }
        }
    }
}

@Composable
fun CircularProgressChart(
    percentage: Float,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(60.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = percentage / 100f,
                modifier = Modifier.fillMaxSize(),
                color = color,
                strokeWidth = 6.dp,
                strokeCap = StrokeCap.Round,
                trackColor = Color(0xFFE0E0E0)
            )
            Text(
                text = "${percentage.toInt()}%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun MusicRecommendationSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "음악 추천",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "마음을 가라앉히는 음악 듣기",
                    fontSize = 14.sp,
                    color = Color.Black
                )
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "더보기",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun OutdoorRecommendationSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "나들이 추천",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = "🌟",
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "오늘은 날씨가 좋아요. 가까운 공원에 가보세요.",
                fontSize = 14.sp,
                color = Color.Gray,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun RecordSection(navController: NavHostController?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "기록 관리",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Row(
                    modifier = Modifier
                        .clickable {
                            navController?.navigate(Screen.RecordDetailScreen.route)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "더보기",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = "더보기",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.Top
            ) {
                // 태동 기록
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "태동 기록",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier.height(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "8.1",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "주간 평균",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                // 구분선
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(80.dp)
                        .background(Color(0xFFE0E0E0))
                )

                // 진통 기록
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "진통 기록",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier.height(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "12회",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "오늘 총 횟수",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WearableRecommendedScreenPreview() {
    WearableRecommendedScreen(navController = null as NavHostController)
}