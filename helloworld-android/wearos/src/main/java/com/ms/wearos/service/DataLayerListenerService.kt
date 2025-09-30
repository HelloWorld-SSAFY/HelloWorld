package com.ms.wearos.service

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import com.ms.wearos.util.WearTokenManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DataLayerListenerService : WearableListenerService() {

    @Inject
    lateinit var wearTokenManager: WearTokenManager

    companion object {
        private const val TAG = "DataLayerListener"
        private const val TOKEN_PATH = "/jwt_token"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DataLayerListenerService created")
        Log.d(TAG, "WearTokenManager initialized: ${::wearTokenManager.isInitialized}")
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        Log.d(TAG, "onDataChanged called, events count: ${dataEvents.count}")

        // 토큰 관련 데이터 변경 감지 및 로깅
        for (event in dataEvents) {
            when (event.type) {
                DataEvent.TYPE_CHANGED -> {
                    val path = event.dataItem.uri.path
                    Log.d(TAG, "Data changed: $path")

                    if (path == TOKEN_PATH) {
                        Log.d(TAG, "Token data received, delegating to WearTokenManager")
                        // WearTokenManager가 자동으로 처리하므로 추가 작업 불필요

                        // 토큰 상태 로깅
                        wearTokenManager.logCurrentTokenStatus()
                    }
                }
                DataEvent.TYPE_DELETED -> {
                    val path = event.dataItem.uri.path
                    Log.d(TAG, "Data deleted: $path")

                    if (path == TOKEN_PATH) {
                        Log.d(TAG, "Token data deleted")
                        wearTokenManager.logCurrentTokenStatus()
                    }
                }
            }
        }

        dataEvents.release()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "DataLayerListenerService destroyed")
    }
}