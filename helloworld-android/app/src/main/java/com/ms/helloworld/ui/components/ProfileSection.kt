package com.ms.helloworld.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ms.helloworld.R
import com.ms.helloworld.dto.response.MomProfile
import com.ms.helloworld.ui.theme.MainColor
import java.time.format.DateTimeFormatter

@Composable
fun ProfileSection(
    momProfile: MomProfile,
    currentPregnancyDay: Int = 1, // 기본값은 1, 실제로는 HomeViewModel에서 전달받은 값 사용
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current
            ) { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        PregnancyProfileImage(
            pregnancyWeek = ((currentPregnancyDay - 1) / 7) + 1,
            size = 80
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${momProfile.nickname}의 임신 정보",
                fontSize = 16.sp,
                color = Color.Gray
            )
            Text(
                text = "${currentPregnancyDay}일째 (${((currentPregnancyDay - 1) / 7) + 1}주차)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "출산예정일 ${momProfile.dueDate.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))}",
                fontSize = 14.sp,
                color = Color.Gray
            )
            Text(
                text = "D-${momProfile.daysUntilDue}",
                fontSize = 14.sp,
                color = MainColor,
                fontWeight = FontWeight.Medium
            )
        }

        Icon(
            painter = painterResource(R.drawable.ic_profile_move),
            contentDescription = "프로필 이동 아이콘",
            tint = Color.Unspecified,
        )
    }
}