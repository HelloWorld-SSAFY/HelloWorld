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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ms.helloworld.R
import kotlin.math.sin

data class RecommendationItem(
    val title: String,
    val backgroundColor: Color
)

@Composable
fun TodayRecommendationSection() {
    val recommendations = listOf(
        RecommendationItem("음식", Color(0xFFB8E6B8)),
        RecommendationItem("현황", Color(0xFFE6B8E6)),
        RecommendationItem("할 일", Color(0xFFB8E6E6)),
    )

    Column {
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
    // title에 따라 아이콘 결정
    val iconRes = when (item.title) {
        "음식" -> R.drawable.ic_food
        "현황" -> R.drawable.ic_food // 또는 적절한 현황 아이콘
        "할 일" -> R.drawable.ic_food // 또는 적절한 할 일 아이콘
        else -> R.drawable.ic_food // 기본 아이콘
    }

    Card(
        modifier = modifier
            .width(120.dp)
            .height(120.dp),
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
            Icon(
                painter = painterResource(iconRes),
                contentDescription = "아이콘",
                tint = Color.Unspecified,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                item.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}