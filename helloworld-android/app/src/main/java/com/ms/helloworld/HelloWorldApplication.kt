package com.ms.helloworld

import android.app.Application
import com.ms.helloworld.notification.NotificationChannels
// import com.kakao.sdk.common.KakaoSdk // Kakao 임시 주석 처리
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HelloWorldApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createDefaultChannel(this)
    }
}