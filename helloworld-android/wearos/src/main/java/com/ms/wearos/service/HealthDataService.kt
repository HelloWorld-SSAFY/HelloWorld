package com.ms.wearos.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.*
import androidx.health.services.client.unregisterMeasureCallback
import kotlinx.coroutines.*
import com.ms.wearos.R

class HealthDataService : Service() {

    companion object {
        const val CHANNEL_ID = "HEALTH_DATA_CHANNEL"
        const val NOTIFICATION_ID = 1
        const val ACTION_START_MEASUREMENT = "START_MEASUREMENT"
        const val ACTION_STOP_MEASUREMENT = "STOP_MEASUREMENT"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val healthServicesClient by lazy { HealthServices.getClient(this) }
    private val measureClient by lazy { healthServicesClient.measureClient }

    private var isCollecting = false
    private var heartRateCallback: MeasureCallback? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d("HealthService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MEASUREMENT -> startHeartRateCollection()
            ACTION_STOP_MEASUREMENT -> stopHeartRateCollection()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "심박수 측정",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "실시간 심박수를 측정합니다"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun startHeartRateCollection() {
        if (isCollecting) return

        val notification = createNotification("심박수 측정 중...")
        startForeground(NOTIFICATION_ID, notification)

        isCollecting = true

        // 심박수 콜백 설정
        heartRateCallback = object : MeasureCallback {
            override fun onAvailabilityChanged(
                dataType: DeltaDataType<*, *>,
                availability: Availability
            ) {
                Log.d("HealthService", "Heart rate availability: $availability")
                updateNotification("심박수 센서: $availability")
            }

            override fun onDataReceived(data: DataPointContainer) {
                val heartRateData = data.getData(DataType.HEART_RATE_BPM)
                heartRateData.forEach { dataPoint ->
                    val bpm = dataPoint.value
                    Log.d("HealthService", "Heart rate: $bpm BPM")
                    saveHeartRateData(bpm)
                    updateNotification("심박수: ${bpm.toInt()} BPM")
                }
            }
        }

        // 심박수 센서 등록
        serviceScope.launch {
            try {
                heartRateCallback?.let { callback ->
                    measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
                }

                Log.d("HealthService", "Heart rate sensor registered successfully")
                startPeriodicLogging()

            } catch (e: Exception) {
                Log.e("HealthService", "Error registering heart rate sensor", e)
                stopSelf()
            }
        }
    }

    private fun stopHeartRateCollection() {
        if (!isCollecting) return

        serviceScope.launch {
            try {
                heartRateCallback?.let { callback ->
                    measureClient.unregisterMeasureCallback(DataType.HEART_RATE_BPM, callback)
                }

                Log.d("HealthService", "Heart rate sensor unregistered")

            } catch (e: Exception) {
                Log.e("HealthService", "Error unregistering heart rate sensor", e)
            } finally {
                isCollecting = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun createNotification(contentText: String): Notification {
        val stopIntent = Intent(this, HealthDataService::class.java).apply {
            action = ACTION_STOP_MEASUREMENT
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("심박수 모니터링")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.splash_icon)
            .setOngoing(true)
            .addAction(R.drawable.splash_icon, "중지", stopPendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun saveHeartRateData(bpm: Double) {
        val timestamp = System.currentTimeMillis()
        Log.d("HeartRateStorage", "[$timestamp] Heart rate: $bpm BPM")

        // SharedPreferences에 최신 심박수 데이터 저장
        val sharedPref = getSharedPreferences("health_data", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("latest_heart_rate", bpm.toString())
            putLong("latest_heart_rate_timestamp", timestamp)
            apply()
        }
    }

    private fun startPeriodicLogging() {
        serviceScope.launch {
            while (isCollecting) {
                delay(30000) // 30초마다

                val sharedPref = getSharedPreferences("health_data", Context.MODE_PRIVATE)
                val heartRate = sharedPref.getString("latest_heart_rate", "0") ?: "0"

                Log.d("HeartRateSummary", """
                    심박수 데이터 [${java.util.Date()}]
                    현재 심박수: ${heartRate} BPM
                """.trimIndent())

                // 심박수 이상 징후 감지
                detectHeartRateAnomalies(heartRate.toDoubleOrNull() ?: 0.0)
            }
        }
    }

    private fun detectHeartRateAnomalies(heartRate: Double) {
        when {
            heartRate > 120 -> {
                val warning = "심박수가 매우 높습니다 (${heartRate.toInt()} BPM)"
                Log.w("HeartRateWarning", warning)
                updateNotification("경고: $warning")
            }
            heartRate < 50 && heartRate > 0 -> {
                val warning = "심박수가 매우 낮습니다 (${heartRate.toInt()} BPM)"
                Log.w("HeartRateWarning", warning)
                updateNotification("경고: $warning")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d("HealthService", "Service destroyed")
    }
}

// 서비스 헬퍼 클래스
object HealthServiceHelper {

    fun startService(context: Context) {
        val intent = Intent(context, HealthDataService::class.java).apply {
            action = HealthDataService.ACTION_START_MEASUREMENT
        }
        context.startForegroundService(intent)
    }

    fun stopService(context: Context) {
        val intent = Intent(context, HealthDataService::class.java).apply {
            action = HealthDataService.ACTION_STOP_MEASUREMENT
        }
        context.startService(intent)
    }
}