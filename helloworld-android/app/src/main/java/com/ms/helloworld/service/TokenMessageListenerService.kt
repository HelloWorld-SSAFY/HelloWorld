package com.ms.helloworld.service

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
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

        // 싱글톤 인스턴스로 외부에서 접근 가능하게 함
        @Volatile
        private var instance: TokenMessageListenerService? = null

        fun getInstance(): TokenMessageListenerService? = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "TokenMessageListenerService created")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "TokenMessageListenerService destroyed")
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
                    sendTokenToWatch(accessToken, refreshToken, "/token_response")
                } else {
                    Log.w(TAG, "전송할 유효한 토큰이 없음")
                    // 워치에 토큰 없음을 알릴 수도 있음
                    sendTokenClearToWatch()
                }

            } catch (e: Exception) {
                Log.e(TAG, "토큰 요청 처리 중 오류", e)
            }
        }
    }

    /**
     * 토큰 갱신 시 워치에 자동 동기화
     * TokenManager에서 호출됨
     */
    fun syncTokensToWatch(accessToken: String, refreshToken: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "토큰 갱신 감지 - 워치로 동기화 시작")
                sendTokenToWatch(accessToken, refreshToken, "/token_sync")
            } catch (e: Exception) {
                Log.e(TAG, "토큰 동기화 중 오류", e)
            }
        }
    }

    /**
     * 토큰 삭제 시 워치에 알림
     * TokenManager에서 호출됨
     */
    fun clearTokensFromWatch() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "토큰 삭제 감지 - 워치에 삭제 신호 전송")
                sendTokenClearToWatch()
            } catch (e: Exception) {
                Log.e(TAG, "토큰 삭제 신호 전송 중 오류", e)
            }
        }
    }

    private suspend fun sendTokenToWatch(
        accessToken: String,
        refreshToken: String,
        messagePath: String
    ) {
        try {
            Log.d(TAG, "워치로 토큰 전송 시작... (path: $messagePath)")
            val messageClient = Wearable.getMessageClient(this)
            val nodeClient = Wearable.getNodeClient(this)

            // 연결된 워치 찾기
            val nodes = nodeClient.connectedNodes.await()
            if (nodes.isEmpty()) {
                Log.w(TAG, "연결된 워치가 없습니다")
                return
            }

            // 토큰 데이터 구성
            val tokenData = mapOf(
                "access_token" to accessToken,
                "refresh_token" to refreshToken,
                "timestamp" to System.currentTimeMillis()
            )

            val jsonData = com.google.gson.Gson().toJson(tokenData)
            val dataBytes = jsonData.toByteArray()

            // 모든 연결된 워치에 토큰 전송
            nodes.forEach { node ->
                try {
                    messageClient.sendMessage(
                        node.id,
                        messagePath,
                        dataBytes
                    ).await()

                    Log.d(TAG, "워치(${node.displayName})로 토큰 전송 완료")
                } catch (e: Exception) {
                    Log.e(TAG, "워치(${node.displayName})로 토큰 전송 실패", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "워치 토큰 전송 실패", e)
        }
    }

    private suspend fun sendTokenClearToWatch() {
        try {
            Log.d(TAG, "워치로 토큰 삭제 신호 전송 시작...")
            val messageClient = Wearable.getMessageClient(this)
            val nodeClient = Wearable.getNodeClient(this)

            // 연결된 워치 찾기
            val nodes = nodeClient.connectedNodes.await()
            if (nodes.isEmpty()) {
                Log.w(TAG, "연결된 워치가 없습니다")
                return
            }

            // 삭제 신호 데이터 구성
            val clearData = mapOf(
                "action" to "clear_tokens",
                "timestamp" to System.currentTimeMillis()
            )

            val jsonData = com.google.gson.Gson().toJson(clearData)
            val dataBytes = jsonData.toByteArray()

            // 모든 연결된 워치에 삭제 신호 전송
            nodes.forEach { node ->
                try {
                    messageClient.sendMessage(
                        node.id,
                        "/token_clear",
                        dataBytes
                    ).await()

                    Log.d(TAG, "워치(${node.displayName})로 토큰 삭제 신호 전송 완료")
                } catch (e: Exception) {
                    Log.e(TAG, "워치(${node.displayName})로 토큰 삭제 신호 전송 실패", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "워치 토큰 삭제 신호 전송 실패", e)
        }
    }
}