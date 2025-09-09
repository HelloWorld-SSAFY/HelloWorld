// BootReceiver.kt - 간소화된 부트 리시버
package com.ms.wearos.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ms.wearos.service.HealthServiceHelper

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received broadcast: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "Device boot completed")
                handleBootCompleted(context)
            }

            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "App package replaced")
                handlePackageReplaced(context)
            }

            Intent.ACTION_PACKAGE_REPLACED -> {
                Log.d(TAG, "Package replaced")
                handlePackageReplaced(context)
            }
        }
    }

    private fun handleBootCompleted(context: Context) {
        // 부팅 완료 시 자동으로 건강 데이터 수집 서비스 시작 여부 확인
        val sharedPref = context.getSharedPreferences("health_settings", Context.MODE_PRIVATE)
        val autoStartEnabled = sharedPref.getBoolean("auto_start_on_boot", false)

        Log.d(TAG, "Auto-start setting: $autoStartEnabled")

        if (autoStartEnabled) {
            Log.d(TAG, "Auto-starting health data service")
            try {
                HealthServiceHelper.startService(context)
                Log.d(TAG, "Health service started successfully on boot")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start health service on boot", e)
            }
        } else {
            Log.d(TAG, "Auto-start is disabled")
        }
    }

    private fun handlePackageReplaced(context: Context) {
        // 앱 업데이트 후 필요한 초기화 작업
        Log.d(TAG, "App updated, performing initialization")

        // 기존 설정 유지하면서 필요한 경우 서비스 재시작
        val sharedPref = context.getSharedPreferences("health_settings", Context.MODE_PRIVATE)
        val wasRunning = sharedPref.getBoolean("service_was_running", false)

        Log.d(TAG, "Service was running before update: $wasRunning")

        if (wasRunning) {
            Log.d(TAG, "Restarting health service after update")
            try {
                HealthServiceHelper.startService(context)
                Log.d(TAG, "Health service restarted successfully after update")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart health service after update", e)
            }
        } else {
            Log.d(TAG, "Service was not running before update, not restarting")
        }
    }
}