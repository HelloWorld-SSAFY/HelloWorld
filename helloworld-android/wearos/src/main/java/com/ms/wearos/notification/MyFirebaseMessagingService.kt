package com.ms.wearos.notification

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ms.helloworld.R
import com.ms.wearos.dto.request.Platforms
import com.ms.wearos.repository.FcmRepository
import com.ms.wearos.ui.theme.MainColor
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var fcmRepository: FcmRepository

    companion object {
        const val TYPE_CALENDAR = "CALENDAR"           // 캘린더 일정 알림
        const val TYPE_HEART_RATE = "HEART_RATE"       // 심박수 이상 알림
        const val TYPE_STRESS = "STRESS"               // 스트레스 지수 이상 알림
        const val TYPE_ACTIVITY = "ACTIVITY"           // 활동량 알림
    }

    override fun onNewToken(token: String) {
        Log.i("FCM", "새 토큰: $token")
        // 로그인 상태에선 서버 등록
        fcmRepository.registerTokenAsync(token = token, Platforms.WATCH)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d("FCM", "FCM 메시지 수신: ${message.data}")

        // 메시지 데이터 추출
        val type = message.data["type"] ?: ""
        val title = message.data["title"] ?: ""
        val body = message.data["body"] ?: ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("FCM", "알림 권한 없음 → 표시 생략")
            return
        }

        // 타입별 알림 설정
        val notificationConfig = createNotificationConfig(type, title, body)

        // 알림 생성 및 표시
        val notification = NotificationCompat.Builder(this, NotificationChannels.DEFAULT)
            .setSmallIcon(notificationConfig.iconRes)
            .setContentTitle(notificationConfig.title)
            .setContentText(notificationConfig.body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationConfig.body))
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        NotificationManagerCompat.from(this).notify(Random.nextInt(), notification)
    }

    /**
     * 알림 타입별 설정 정보 생성
     */
    private fun createNotificationConfig(type: String, title: String, body: String): NotificationConfig {
        return when (type) {
            TYPE_CALENDAR -> {
                NotificationConfig(
                    title = title.ifEmpty { "일정 알림" },
                    body = body.ifEmpty { "예정된 일정이 있습니다" },
                    iconRes = R.drawable.ic_calendar,
                    priority = NotificationCompat.PRIORITY_DEFAULT,
                    colorRes = MainColor
                )
            }
            TYPE_HEART_RATE -> {
                NotificationConfig(
                    title = title.ifEmpty { "❤ 심박수 알림" },
                    body = body.ifEmpty { "심박수가 정상 범위를 벗어났습니다" },
                    iconRes = R.drawable.ic_heart,
                    priority = NotificationCompat.PRIORITY_DEFAULT,
                    colorRes = MainColor
                )
            }
            TYPE_STRESS -> {
                NotificationConfig(
                    title = title.ifEmpty { "스트레스 알림" },
                    body = body.ifEmpty { "스트레스 지수가 높습니다. 휴식을 취해보세요" },
                    iconRes = R.drawable.ic_stress,
                    priority = NotificationCompat.PRIORITY_DEFAULT,
                    colorRes = MainColor
                )
            }
            TYPE_ACTIVITY -> {
                NotificationConfig(
                    title = title.ifEmpty { "활동량 알림" },
                    body = body.ifEmpty { "오늘 활동량이 부족합니다. 조금 더 움직여보세요!" },
                    iconRes = R.drawable.ic_activity,
                    priority = NotificationCompat.PRIORITY_DEFAULT,
                    colorRes = MainColor
                )
            }
            else -> {
                NotificationConfig(
                    title = title.ifEmpty { "알림" },
                    body = body.ifEmpty { "새로운 알림이 도착했습니다" },
                    iconRes = R.drawable.ic_noti,
                    priority = NotificationCompat.PRIORITY_DEFAULT,
                    colorRes = MainColor
                )
            }
        }
    }

    /**
     * 알림 설정 데이터 클래스
     */
    private data class NotificationConfig(
        val title: String,
        val body: String,
        val iconRes: Int,
        val priority: Int,
        val colorRes: Color
    )
}