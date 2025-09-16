package com.ms.helloworld.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.ms.helloworld.ui.components.CustomTopAppBar

data class NotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val timestamp: String,
    val isRead: Boolean = false
)

@Composable
fun NotificationScreen(
    navController: NavHostController
) {
    val sampleNotifications = remember {
        listOf(
            NotificationItem(
                id = "1",
                title = "건강 체크 알림",
                message = "오늘의 건강 상태를 기록해주세요!",
                timestamp = "2024-01-15 09:00",
                isRead = false
            ),
            NotificationItem(
                id = "2",
                title = "일기 작성 알림",
                message = "오늘 하루는 어떠셨나요? 일기를 작성해보세요.",
                timestamp = "2024-01-14 19:00",
                isRead = true
            ),
            NotificationItem(
                id = "3",
                title = "병원 방문 일정",
                message = "내일 오후 2시 정기검진 예약이 있습니다.",
                timestamp = "2024-01-13 10:30",
                isRead = false
            ),
            NotificationItem(
                id = "4",
                title = "웨어러블 연동 완료",
                message = "웨어러블 기기가 성공적으로 연동되었습니다.",
                timestamp = "2024-01-12 14:20",
                isRead = true
            ),
            NotificationItem(
                id = "5",
                title = "주간 건강 리포트",
                message = "이번 주 건강 상태 요약을 확인해보세요.",
                timestamp = "2024-01-11 08:00",
                isRead = true
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        CustomTopAppBar(
            title = "알림",
            navController = navController
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            items(sampleNotifications.size) { index ->
                val notification = sampleNotifications[index]
                NotificationItem(notification = notification)

                if (index < sampleNotifications.size - 1) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = Color.LightGray
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationItem(
    notification: NotificationItem
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = notification.title,
                fontSize = 16.sp,
                fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.weight(1f)
            )

            if (!notification.isRead) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .padding(top = 4.dp)
                ) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        drawCircle(color = androidx.compose.ui.graphics.Color.Red)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = notification.message,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = Color.Gray,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = notification.timestamp,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = Color.LightGray
        )
    }
}

@Preview(showBackground = true)
@Composable
fun NotificationScreenPreview() {
    NotificationScreen(navController = null as NavHostController)
}