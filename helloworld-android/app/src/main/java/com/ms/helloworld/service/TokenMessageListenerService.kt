package com.ms.helloworld.service

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.ms.helloworld.util.TokenManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
class TokenMessageListenerService : WearableListenerService() {

    @Inject
    lateinit var tokenManager: TokenManager

    companion object {
        private const val TAG = "TokenMessageListener"
        private const val TOKEN_PATH = "/jwt_token"
        private const val ACCESS_TOKEN_KEY = "access_token"
        private const val REFRESH_TOKEN_KEY = "refresh_token"
        private const val TIMESTAMP_KEY = "timestamp"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TokenMessageListenerService created")
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        Log.d(TAG, "Message received: ${messageEvent.path} from ${messageEvent.sourceNodeId}")

        when (messageEvent.path) {
            "/request_token" -> {
                Log.d(TAG, "워치에서 토큰 요청 받음")
                handleTokenRequest(messageEvent.sourceNodeId)
            }
            else -> {
                Log.d(TAG, "Unknown message path: ${messageEvent.path}")
            }
        }
    }

    private fun handleTokenRequest(nodeId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "토큰 요청 처리 시작...")

                // 현재 저장된 토큰 조회
                val accessToken = tokenManager.getAccessTokenSuspend()
                val refreshToken = tokenManager.getRefreshTokenSuspend()

                if (!accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()) {
                    Log.d(TAG, "유효한 토큰 발견 - 워치로 전송")
                    sendTokenToWatch(accessToken, refreshToken)
                } else {
                    Log.w(TAG, "전송할 유효한 토큰이 없음")
                    // 워치에 토큰 없음을 알릴 수도 있음 (선택사항)
                }

            } catch (e: Exception) {
                Log.e(TAG, "토큰 요청 처리 중 오류", e)
            }
        }
    }

    private suspend fun sendTokenToWatch(accessToken: String, refreshToken: String) {
        try {
            Log.d(TAG, "워치로 토큰 전송 시작...")
            val dataClient = Wearable.getDataClient(this)

            val putDataMapRequest = PutDataMapRequest.create(TOKEN_PATH).apply {
                dataMap.putString(ACCESS_TOKEN_KEY, accessToken)
                dataMap.putString(REFRESH_TOKEN_KEY, refreshToken)
                dataMap.putLong(TIMESTAMP_KEY, System.currentTimeMillis())
            }

            val putDataRequest = putDataMapRequest.asPutDataRequest()
            putDataRequest.setUrgent()

            val result = dataClient.putDataItem(putDataRequest).await()
            Log.d(TAG, "워치로 토큰 전송 완료 - URI: ${result.uri}")

        } catch (e: Exception) {
            Log.e(TAG, "워치 토큰 전송 실패", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "TokenMessageListenerService destroyed")
    }
}