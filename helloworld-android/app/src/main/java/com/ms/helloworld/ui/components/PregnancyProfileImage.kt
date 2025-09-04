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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ms.helloworld.R

@Composable
fun PregnancyProfileImage(
    pregnancyWeek: Int,
    modifier: Modifier = Modifier,
    size: Int = 60
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
            modifier = Modifier.size((size * 0.8).toInt().dp)
        )
    }
}

private fun getDrawableResource(pregnancyWeek: Int): Int {
    return when (pregnancyWeek) {
        in 1..10 -> R.drawable.week1
        in 11..20 -> R.drawable.week2
        in 21..30 -> R.drawable.week3
        in 31..40 -> R.drawable.week4
        else -> R.drawable.week1 // 기본값
    }
}