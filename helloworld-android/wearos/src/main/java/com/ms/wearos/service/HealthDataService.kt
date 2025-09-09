// HealthDataService.kt - 심박수와 활동량만 다루는 간소화된 서비스
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
    private var activityCallback: MeasureCallback? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d("HealthService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MEASUREMENT -> startHealthDataCollection()
            ACTION_STOP_MEASUREMENT -> stopHealthDataCollection()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "건강 데이터 수집",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "실시간 건강 데이터를 수집합니다"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun startHealthDataCollection() {
        if (isCollecting) return

        val notification = createNotification("건강 데이터 수집 중...")
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
                    saveHealthData("heart_rate", bpm.toString())
                    updateNotification("심박수: ${bpm.toInt()} BPM")
                }
            }
        }

        // 활동량 콜백 설정
        activityCallback = object : MeasureCallback {
            override fun onAvailabilityChanged(
                dataType: DeltaDataType<*, *>,
                availability: Availability
            ) {
                Log.d("HealthService", "Activity sensor availability: $availability")
            }

            override fun onDataReceived(data: DataPointContainer) {
                // 걸음수
                data.getData(DataType.STEPS).forEach { dataPoint ->
                    Log.d("HealthService", "Steps: ${dataPoint.value}")
                    saveHealthData("steps", dataPoint.value.toString())
                }

                // 칼로리
                data.getData(DataType.CALORIES).forEach { dataPoint ->
                    Log.d("HealthService", "Calories: ${dataPoint.value}")
                    saveHealthData("calories", dataPoint.value.toString())
                }

                // 거리
                data.getData(DataType.DISTANCE).forEach { dataPoint ->
                    Log.d("HealthService", "Distance: ${dataPoint.value}")
                    saveHealthData("distance", dataPoint.value.toString())
                }
            }
        }

        // 센서 등록
        serviceScope.launch {
            try {
                heartRateCallback?.let { callback ->
                    measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
                }

                activityCallback?.let { callback ->
                    measureClient.registerMeasureCallback(DataType.STEPS, callback)
                    measureClient.registerMeasureCallback(DataType.CALORIES, callback)
                    measureClient.registerMeasureCallback(DataType.DISTANCE, callback)
                }

                Log.d("HealthService", "All sensors registered successfully")
                startPeriodicLogging()

            } catch (e: Exception) {
                Log.e("HealthService", "Error registering sensors", e)
                stopSelf()
            }
        }
    }

    private fun stopHealthDataCollection() {
        if (!isCollecting) return

        serviceScope.launch {
            try {
                heartRateCallback?.let { callback ->
                    measureClient.unregisterMeasureCallback(DataType.HEART_RATE_BPM, callback)
                }

                activityCallback?.let { callback ->
                    measureClient.unregisterMeasureCallback(DataType.STEPS, callback)
                    measureClient.unregisterMeasureCallback(DataType.CALORIES, callback)
                    measureClient.unregisterMeasureCallback(DataType.DISTANCE, callback)
                }

                Log.d("HealthService", "All sensors unregistered")

            } catch (e: Exception) {
                Log.e("HealthService", "Error unregistering sensors", e)
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
            .setContentTitle("건강 데이터 모니터링")
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

    private fun saveHealthData(type: String, value: String) {
        val timestamp = System.currentTimeMillis()
        Log.d("HealthDataStorage", "[$timestamp] $type: $value")

        // SharedPreferences에 최신 데이터 저장
        val sharedPref = getSharedPreferences("health_data", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("latest_$type", value)
            putLong("latest_${type}_timestamp", timestamp)
            apply()
        }
    }

    private fun startPeriodicLogging() {
        serviceScope.launch {
            while (isCollecting) {
                delay(30000) // 30초마다

                val sharedPref = getSharedPreferences("health_data", Context.MODE_PRIVATE)
                val heartRate = sharedPref.getString("latest_heart_rate", "0") ?: "0"
                val steps = sharedPref.getString("latest_steps", "0") ?: "0"
                val calories = sharedPref.getString("latest_calories", "0") ?: "0"
                val distance = sharedPref.getString("latest_distance", "0") ?: "0"

                Log.d("HealthSummary", """
                    건강 데이터 요약 [${java.util.Date()}]
                    심박수: ${heartRate} BPM
                    걸음수: ${steps}
                    칼로리: ${calories} kcal
                    거리: ${distance} m
                """.trimIndent())

                // 이상 징후 감지
                detectAnomalies(heartRate.toDoubleOrNull() ?: 0.0, steps.toIntOrNull() ?: 0)
            }
        }
    }

    private fun detectAnomalies(heartRate: Double, steps: Int) {
        val warnings = mutableListOf<String>()

        when {
            heartRate > 120 -> warnings.add("심박수가 매우 높습니다 (${heartRate.toInt()} BPM)")
            heartRate < 50 && heartRate > 0 -> warnings.add("심박수가 매우 낮습니다 (${heartRate.toInt()} BPM)")
        }

        if (steps < 100 && System.currentTimeMillis() % (1000 * 60 * 60) == 0L) {
            warnings.add("활동량이 부족합니다. 움직이는 것을 권장합니다.")
        }

        warnings.forEach { warning ->
            Log.w("HealthWarning", warning)
            updateNotification("경고: $warning")
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