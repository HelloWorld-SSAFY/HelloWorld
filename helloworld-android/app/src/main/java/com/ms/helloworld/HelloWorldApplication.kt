package com.ms.helloworld

import android.app.Application
// import com.kakao.sdk.common.KakaoSdk // Kakao 임시 주석 처리
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HelloWorldApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Kakao SDK 초기화 - 임시 주석 처리
        // KakaoSdk.init(this, "YOUR_KAKAO_APP_KEY") // TODO: 실제 Kakao App Key로 변경
    }
}