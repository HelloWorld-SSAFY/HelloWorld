package com.ms.helloworld.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class RecommendationItem(
    val title: String,
    val backgroundColor: Color,
    val emoji: String
)

@Composable
fun TodayRecommendationSection() {
    val recommendations = listOf(
        RecommendationItem("음식", Color(0xFFB8E6B8), "🥗"),
        RecommendationItem("스트레칭", Color(0xFFE6B8E6), "🧘‍♀️"),
        RecommendationItem("할일", Color(0xFFB8E6E6), "💧"),
        RecommendationItem("휴식", Color(0xFFF5E6B8), "😴"),
        RecommendationItem("운동", Color(0xFFFFB8B8), "🏃‍♀️"),
        RecommendationItem("독서", Color(0xFFB8D4FF), "📚"),
        RecommendationItem("명상", Color(0xFFDDB8FF), "🧘"),
        RecommendationItem("산책", Color(0xFFB8FFB8), "🚶‍♀️")
    )

    Column {
        Text(
            "오늘의 추천",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(recommendations) { item ->
                RecommendationCard(
                    item = item
                )
            }
        }
    }
}

@Composable
fun RecommendationCard(
    item: RecommendationItem,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(100.dp)
            .height(100.dp),
        colors = CardDefaults.cardColors(containerColor = item.backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                item.emoji,
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                item.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}