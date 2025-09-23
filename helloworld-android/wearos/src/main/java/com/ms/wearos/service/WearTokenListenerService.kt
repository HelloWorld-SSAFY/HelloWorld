package com.ms.wearos.service

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.ms.wearos.util.WearTokenManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WearTokenListenerService : WearableListenerService() {

    @Inject
    lateinit var tokenManager: WearTokenManager

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        Log.d("WearTokenListener", "메시지 수신: ${messageEvent.path}")

        when (messageEvent.path) {
            "/token_response" -> {
                Log.d("WearTokenListener", "토큰 응답 받음")
                handleTokenResponse(messageEvent.data)
            }
        }
    }

    private fun handleTokenResponse(data: ByteArray) {
        try {
            val jsonData = String(data)
            val gson = com.google.gson.Gson()
            val tokenData = gson.fromJson(jsonData,
                object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
            ) as Map<String, Any>

            val accessToken = tokenData["access_token"] as? String
            val refreshToken = tokenData["refresh_token"] as? String

            if (!accessToken.isNullOrEmpty() && !refreshToken.isNullOrEmpty()) {
                tokenManager.saveTokens(accessToken, refreshToken)
                Log.d("WearTokenListener", "토큰 수신 및 저장 완료")
            }

        } catch (e: Exception) {
            Log.e("WearTokenListener", "토큰 응답 처리 실패", e)
        }
    }
}