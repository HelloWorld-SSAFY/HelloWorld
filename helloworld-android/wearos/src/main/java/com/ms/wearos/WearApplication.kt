package com.ms.wearos

import android.app.Application
import com.ms.wearos.notification.NotificationChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WearApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createDefaultChannel(this)
    }
}