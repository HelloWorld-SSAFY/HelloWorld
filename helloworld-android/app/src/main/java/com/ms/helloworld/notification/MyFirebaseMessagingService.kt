package com.ms.helloworld.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ms.helloworld.MainActivity
import com.ms.helloworld.R
import com.ms.helloworld.dto.request.Platforms
import com.ms.helloworld.repository.AuthRepository
import com.ms.helloworld.repository.FcmRepository
import com.ms.helloworld.ui.theme.MainColor
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var fcmRepository: FcmRepository

    companion object {
        const val TYPE_CALENDAR = "CALENDAR"           // 캘린더 일정 알림
        const val TYPE_HEART_RATE = "HEART_RATE"       // 심박수 이상 알림
        const val TYPE_STRESS = "STRESS"               // 스트레스 지수 이상 알림
        const val TYPE_ACTIVITY = "ACTIVITY"           // 활동량 알림
    }

    override fun onNewToken(token: String) {
        Log.i("FCM", "새 토큰: $token")
        // 로그인 상태에선 서버 등록
        fcmRepository.registerTokenAsync(token = token, Platforms.ANDROID)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d("FCM", "FCM 메시지 수신: ${message.data}")

        // 메시지 데이터 추출
        val type = message.data["type"] ?: ""
        val title = message.data["title"] ?: ""
        val coupleId = message.data["coupleId"] ?: ""
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

        // 딥링크 Intent 생성
        val intent = createDeepLinkIntent(type, coupleId)
        val pendingIntent = PendingIntent.getActivity(
            this,
            Random.nextInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )


        // 알림 생성 및 표시
        val notification = NotificationCompat.Builder(this, NotificationChannels.DEFAULT)
            .setSmallIcon(notificationConfig.iconRes)
            .setContentTitle(notificationConfig.title)
            .setContentText(notificationConfig.body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationConfig.body))
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
                    title = title.ifEmpty { "심박수 알림" },
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
     * 딥링크 Intent 생성 - type에 따라 이동할 화면 결정, coupleId로 데이터 조회
     */
    private fun createDeepLinkIntent(type: String, coupleId: String): Intent {
        return Intent(this, MainActivity::class.java).apply {
            action = "FCM_NOTIFICATION_$type"
            putExtra("notification_timestamp", System.currentTimeMillis())
            putExtra("notification_type", type)
            putExtra("coupleId", coupleId) // 커플 ID 전달

            // 앱이 이미 실행 중일 때 새 액티비티를 생성하지 않고 기존 액티비티로 이동
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP

            // 타입별 딥링크 설정
            when (type) {
                TYPE_CALENDAR -> {
                    putExtra("deeplink_type", "calendar")
                    // 캘린더 화면으로 이동 + coupleId로 해당 커플의 일정 조회
                }
                TYPE_HEART_RATE -> {
                    putExtra("deeplink_type", "health_heart_rate")
                    // 웨어러블 추천 화면으로 이동 + coupleId로 해당 커플의 심박수 데이터 조회
                }
                TYPE_STRESS -> {
                    putExtra("deeplink_type", "health_stress")
                    // 웨어러블 추천 화면으로 이동 + coupleId로 해당 커플의 스트레스 데이터 조회
                }
                TYPE_ACTIVITY -> {
                    putExtra("deeplink_type", "health_activity")
                    // 웨어러블 추천 화면으로 이동 + coupleId로 해당 커플의 활동량 데이터 조회
                }
                else -> {
                    putExtra("deeplink_type", "main")
                    // 메인 화면으로 이동
                }
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
