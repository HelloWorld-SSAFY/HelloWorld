package com.ms.wearos.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val DEFAULT = "default"

    /**
     * 기본 알림 채널을 생성합니다
     */
    fun createDefaultChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)

            val defaultChannel = NotificationChannel(
                DEFAULT,
                "일반 알림",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "헬스케어 관련 모든 알림"
            }
            notificationManager.createNotificationChannel(defaultChannel)
        }
    }
}