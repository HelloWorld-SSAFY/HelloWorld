package com.ms.helloworld

import android.app.Application
import com.ms.helloworld.notification.NotificationChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HelloWorldApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createDefaultChannel(this)
    }
}