package com.ms.helloworld.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ms.helloworld.data.MomProfile
import java.time.format.DateTimeFormatter

@Composable
fun ProfileSection(
    momProfile: MomProfile,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PregnancyProfileImage(
            pregnancyWeek = momProfile.pregnancyWeek,
            size = 60
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${momProfile.nickname}",
                fontSize = 14.sp,
                color = Color.Gray
            )
            Text(
                text = "${momProfile.currentDay}일째 (${momProfile.pregnancyWeek}주차)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "출산예정일 ${momProfile.dueDate.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))}",
                fontSize = 14.sp,
                color = Color.Gray
            )
            Text(
                text = "D-${momProfile.getDaysUntilDue()}",
                fontSize = 12.sp,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Medium
            )
        }

        Text(
            ">",
            fontSize = 18.sp,
            color = Color.Gray
        )
    }
}