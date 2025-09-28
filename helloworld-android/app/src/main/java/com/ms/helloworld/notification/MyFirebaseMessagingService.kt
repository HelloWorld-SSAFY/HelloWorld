package com.ms.helloworld.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
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
import com.ms.helloworld.repository.FcmRepository
import com.ms.helloworld.ui.theme.MainColor
import com.ms.helloworld.util.TokenManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var fcmRepository: FcmRepository
    @Inject lateinit var tokenManager: TokenManager

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val TYPE_REMINDER = "REMINDER"
        const val TYPE_EMERGENCY = "EMERGENCY"
        private const val FCM_PREFS = "fcm_prefs"
        private const val FCM_TOKEN_KEY = "fcm_token"
    }

    override fun onNewToken(token: String) {
        Log.i("FCM", "새 토큰: $token")
        saveTokenToLocal(token)

        serviceScope.launch {
            try {
                val accessToken = tokenManager.getAccessToken()
                if (!accessToken.isNullOrEmpty()) {
                    Log.d("FCM", "로그인 상태 확인됨, 토큰 등록 시도")
                    fcmRepository.registerTokenAsync(token = token, Platforms.ANDROID)
                } else {
                    Log.d("FCM", "로그인 상태 아님, 로컬에만 저장")
                }
            } catch (e: Exception) {
                Log.w("FCM", "토큰 등록 실패: ${e.message}")
            }
        }
    }

    private fun saveTokenToLocal(token: String) {
        try {
            val sharedPrefs = getSharedPreferences(FCM_PREFS, Context.MODE_PRIVATE)
            sharedPrefs.edit().putString(FCM_TOKEN_KEY, token).apply()
            Log.d("FCM", "토큰 로컬 저장 완료")
        } catch (e: Exception) {
            Log.e("FCM", "토큰 로컬 저장 실패: ${e.message}")
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d("FCM", "FCM 메시지 수신: ${message.data}")

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

        val notificationConfig = createNotificationConfig(type, title, body)
        val intent = createDeepLinkIntent(type)
        val pendingIntent = PendingIntent.getActivity(
            this,
            Random.nextInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 헤드업 알림을 위한 설정 강화
        val notification = NotificationCompat.Builder(this, notificationConfig.channelId)
            .setSmallIcon(notificationConfig.iconRes)
            .setContentTitle(notificationConfig.title)
            .setContentText(notificationConfig.body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // HIGH 필수
            .setCategory(NotificationCompat.CATEGORY_MESSAGE) // 카테고리 추가
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationConfig.body))
            .setDefaults(NotificationCompat.DEFAULT_ALL) // 기본 설정 모두 사용
            .setVibrate(longArrayOf(0, 500, 200, 500)) // 진동 패턴
            .setLights(0xFF00FF00.toInt(), 3000, 3000) // LED 설정
            .setFullScreenIntent(pendingIntent, false) // 전체 화면은 false
            .build()

        NotificationManagerCompat.from(this).notify(Random.nextInt(), notification)
        Log.d("FCM", "알림 표시 완료 - 타입: $type")
    }

    private fun createNotificationConfig(type: String, title: String, body: String): NotificationConfig {
        return when (type) {
            TYPE_REMINDER -> {
                NotificationConfig(
                    title = title.ifEmpty { "일정 알림" },
                    body = body.ifEmpty { "예정된 일정이 있습니다." },
                    iconRes = R.drawable.ic_calendar,
                    channelId = NotificationChannels.REMINDER
                )
            }
            TYPE_EMERGENCY -> {
                NotificationConfig(
                    title = title.ifEmpty { "위험 알림" },
                    body = body.ifEmpty { "웨어러블 데이터가 정상 범위를 초과했습니다." },
                    iconRes = R.drawable.ic_wear_noti,
                    channelId = NotificationChannels.EMERGENCY
                )
            }
            else -> {
                NotificationConfig(
                    title = title.ifEmpty { "알림" },
                    body = body.ifEmpty { "새로운 알림이 도착했습니다" },
                    iconRes = R.drawable.ic_noti,
                    channelId = NotificationChannels.DEFAULT
                )
            }
        }
    }

    private fun createDeepLinkIntent(type: String): Intent {
        return Intent(this, MainActivity::class.java).apply {
            action = "FCM_NOTIFICATION_$type"
            putExtra("notification_timestamp", System.currentTimeMillis())
            putExtra("notification_type", type)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP

            when (type) {
                TYPE_REMINDER -> {
                    putExtra("deeplink_type", "REMINDER")
                }
                TYPE_EMERGENCY -> {
                    putExtra("deeplink_type", "EMERGENCY")
                }
                else -> {
                    putExtra("deeplink_type", "main")
                }
            }
        }
    }

    private data class NotificationConfig(
        val title: String,
        val body: String,
        val iconRes: Int,
        val channelId: String
    )
}