package com.ms.helloworld.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ms.helloworld.R
import com.ms.helloworld.ui.theme.MainColor

data class RecommendationItem(
    val title: String,
    val backgroundColor: Color
)

@Composable
fun TodayRecommendationSection() {
    val recommendations = listOf(
        RecommendationItem("음식", Color(0xFFFFFFFF)),
        RecommendationItem("할 일", Color(0xFFFFFFFF)),
        RecommendationItem("현황", Color(0xFFFFFFFF)),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        recommendations.forEach { item ->
            RecommendationCard(item = item, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun RecommendationCard(
    item: RecommendationItem,
    modifier: Modifier = Modifier
) {
    // title에 따라 아이콘 결정
    val iconRes = when (item.title) {
        "음식" -> R.drawable.ic_food
        "할 일" -> R.drawable.ic_todo
        "현황" -> R.drawable.ic_steps
        else -> R.drawable.ic_food // 기본 아이콘
    }

    Card(
        modifier = modifier
            .height(120.dp),
        colors = CardDefaults.cardColors(containerColor = item.backgroundColor),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.5.dp,
            color = MainColor.copy(alpha = 0.3f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = "아이콘",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    item.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}