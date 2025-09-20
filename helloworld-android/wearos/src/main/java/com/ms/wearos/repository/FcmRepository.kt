package com.ms.wearos.repository

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.ms.wearos.dto.request.FcmRegisterRequest
import com.ms.wearos.network.api.FcmApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FcmRepository @Inject constructor(
    private val api: FcmApi
) {
    fun registerTokenAsync(token: String? = null, platform: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val t = token ?: FirebaseMessaging.getInstance().token.await()
                val fcmTokenRequest = FcmRegisterRequest(t, platform)

                val res = api.registerFcmToken(fcmTokenRequest)
                if (res.isSuccessful) {
                    Log.i("FCM", "토큰 등록 성공")
                } else {
                    Log.w("FCM", "토큰 등록 실패: ${res.code()} ${res.message()}")
                }
            } catch (e: Exception) {
                Log.e("FCM", "토큰 등록 중 예외", e)
            }
        }
    }
}