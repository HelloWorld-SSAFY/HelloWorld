package com.ms.wearos.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ms.wearos.service.HealthServiceHelper

private const val TAG = "HealthBootReceiver"

class BootReceiver : BroadcastReceiver() {

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
                val packageName = intent.data?.schemeSpecificPart
                if (packageName == context.packageName) {
                    Log.d(TAG, "This app package replaced")
                    handlePackageReplaced(context)
                }
            }
        }
    }

    private fun handleBootCompleted(context: Context) {
        // 부팅 완료 시 저장된 토글 상태 확인하여 서비스 시작
        val sharedPref = context.getSharedPreferences("heart_rate_prefs", Context.MODE_PRIVATE)
        val toggleEnabled = sharedPref.getBoolean("heart_rate_toggle", false)

        Log.d(TAG, "Boot completed - Toggle state: $toggleEnabled")

        if (toggleEnabled) {
            Log.d(TAG, "Auto-starting heart rate service on boot")
            try {
                HealthServiceHelper.startService(context)
                Log.d(TAG, "Heart rate service started successfully on boot")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start heart rate service on boot", e)
            }
        } else {
            Log.d(TAG, "Heart rate toggle is disabled, not starting service")
        }
    }

    private fun handlePackageReplaced(context: Context) {
        // 앱 업데이트 후 저장된 토글 상태 확인하여 서비스 재시작
        Log.d(TAG, "App updated, checking saved toggle state")

        val sharedPref = context.getSharedPreferences("heart_rate_prefs", Context.MODE_PRIVATE)
        val toggleEnabled = sharedPref.getBoolean("heart_rate_toggle", false)
        val serviceWasRunning = sharedPref.getBoolean("service_was_running", false)

        Log.d(TAG, "After update - Toggle: $toggleEnabled, Service was running: $serviceWasRunning")

        if (toggleEnabled || serviceWasRunning) {
            Log.d(TAG, "Restarting heart rate service after app update")
            try {
                HealthServiceHelper.startService(context)
                Log.d(TAG, "Heart rate service restarted successfully after update")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart heart rate service after update", e)
            }
        } else {
            Log.d(TAG, "Service was not running before update, not restarting")
        }
    }
}