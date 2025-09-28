package com.ms.helloworld.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val DEFAULT = "default_channel"
    const val EMERGENCY = "emergency_channel"
    const val REMINDER = "reminder_channel"

    fun createDefaultChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 기본 채널
            val defaultChannel = NotificationChannel(
                DEFAULT,
                "기본 알림",
                NotificationManager.IMPORTANCE_HIGH // HIGH로 변경
            ).apply {
                description = "일반적인 알림"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }

            // 응급 알림 채널
            val emergencyChannel = NotificationChannel(
                EMERGENCY,
                "응급 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "웨어러블 데이터 이상 알림"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500) // 진동 패턴
            }

            // 일정 알림 채널
            val reminderChannel = NotificationChannel(
                REMINDER,
                "일정 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "일정 및 리마인더 알림"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(defaultChannel)
            notificationManager.createNotificationChannel(emergencyChannel)
            notificationManager.createNotificationChannel(reminderChannel)
        }
    }
}