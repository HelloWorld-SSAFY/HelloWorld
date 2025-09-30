package com.ms.helloworld.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HealthStatusSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 차트 표현 (간단한 막대 그래프)
            Row(
                modifier = Modifier.height(120.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HealthBar(height = 60.dp, color = Color.Blue)
                HealthBar(height = 100.dp, color = Color.Green)
                HealthBar(height = 80.dp, color = Color.Yellow)
                HealthBar(height = 90.dp, color = Color.Red)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 연결선 표현
            Canvas(modifier = Modifier.fillMaxWidth().height(20.dp)) { }
        }
    }
}

@Composable
fun HealthBar(height: Dp, color: Color) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(height)
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .background(color)
    )
}