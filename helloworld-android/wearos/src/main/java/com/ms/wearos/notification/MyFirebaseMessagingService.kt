package com.ms.wearos.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor.TYPE_HEART_RATE
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
import com.ms.wearos.ui.MainActivity
import com.ms.wearos.ui.theme.MainColor
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var fcmRepository: FcmRepository

    companion object {
        const val TYPE_REMINDER = "REMINDER"           // 캘린더 일정 알림
        const val TYPE_EMERGENCY = "EMERGENCY"       // 심박수 이상 알림
    }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createDefaultChannel(this)
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

        val channelId = when (type) {
            TYPE_EMERGENCY -> NotificationChannels.EMERGENCY
            TYPE_REMINDER  -> NotificationChannels.REMINDER
            else           -> NotificationChannels.REMINDER
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra("fromPushType", type)
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0, // 고정 권장
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(
                when (type) {
                    TYPE_EMERGENCY -> R.drawable.ic_wear_noti
                    TYPE_REMINDER  -> R.drawable.ic_calendar
                    else           -> R.drawable.ic_noti
                }
            )
            .setContentTitle(
                if (title.isNotBlank()) title else when (type) {
                    TYPE_EMERGENCY -> "위험 알림"
                    TYPE_REMINDER  -> "일정 알림"
                    else           -> "알림"
                }
            )
            .setContentText(body.ifBlank { defaultBody(type) })
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.ifBlank { defaultBody(type) }))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 하위버전 호환용
            .setCategory(
                when (type) {
                    TYPE_EMERGENCY -> NotificationCompat.CATEGORY_ALARM
                    TYPE_REMINDER  -> NotificationCompat.CATEGORY_REMINDER
                    else           -> NotificationCompat.CATEGORY_MESSAGE
                }
            )
            .setDefaults(NotificationCompat.DEFAULT_ALL) // 사운드/진동/라이트
            .build()

        NotificationManagerCompat.from(this).notify(Random.nextInt(), notification)
    }

    /**
     * 알림 타입별 설정 정보 생성
     */
    private fun createNotificationConfig(type: String, title: String, body: String): NotificationConfig {
        return when (type) {
            TYPE_REMINDER -> {
                NotificationConfig(
                    title = title.ifEmpty { "일정 알림" },
                    body = body.ifEmpty { "예정된 일정이 있습니다" },
                    iconRes = R.drawable.ic_calendar,
                    priority = NotificationCompat.PRIORITY_DEFAULT,
                    colorRes = MainColor
                )
            }
            TYPE_EMERGENCY -> {
                NotificationConfig(
                    title = title.ifEmpty { "위험 알림" },
                    body = body.ifEmpty { "웨어러블 데이터가 정상 범위를 초과했습니다." },
                    iconRes = R.drawable.ic_wear_noti,
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

    private fun defaultBody(type: String) = when (type) {
        TYPE_EMERGENCY -> "웨어러블 데이터가 정상 범위를 초과했습니다."
        TYPE_REMINDER  -> "예정된 일정이 있습니다."
        else           -> "새로운 알림이 도착했습니다."
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