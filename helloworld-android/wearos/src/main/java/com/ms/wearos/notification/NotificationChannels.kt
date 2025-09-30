package com.ms.wearos.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val EMERGENCY = "EMERGENCY"
    const val REMINDER  = "REMINDER"

    /**
     * 기본 알림 채널을 생성합니다
     */
    fun createDefaultChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)

        val emergency = NotificationChannel(
            EMERGENCY,
            "위험 알림",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "심박/스트레스 이상 감지 알림"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
        }

        val reminder = NotificationChannel(
            REMINDER,
            "일정 알림",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "캘린더/리마인더 알림"
            enableVibration(true)
        }

        nm.createNotificationChannel(emergency)
        nm.createNotificationChannel(reminder)
    }
}