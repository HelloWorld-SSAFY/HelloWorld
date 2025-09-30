package com.ms.helloworld.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ms.helloworld.R

@Composable
fun PregnancyProfileImage(
    pregnancyWeek: Int,
    modifier: Modifier = Modifier,
    size: Int = 80
) {
    val drawableRes = getDrawableResource(pregnancyWeek)
    
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = "${pregnancyWeek}주차 프로필 이미지",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop     // 비율 유지하며 꽉 채우기
        )
    }
}

private fun getDrawableResource(pregnancyWeek: Int): Int {
    return when (pregnancyWeek) {
        in 1..10 -> R.drawable.pregnant_woman
        in 11..20 -> R.drawable.pregnant_woman
        in 21..30 -> R.drawable.pregnant_woman
        in 31..40 -> R.drawable.pregnant_woman
        else -> R.drawable.pregnant_woman // 기본값
    }
}