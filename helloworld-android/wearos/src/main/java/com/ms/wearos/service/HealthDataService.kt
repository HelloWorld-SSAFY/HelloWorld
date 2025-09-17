package com.ms.wearos.service

import android.annotation.SuppressLint
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
import com.ms.wearos.util.TestLoggingUtils
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "싸피_HealthDataService"
class HealthDataService : Service() {

    companion object {
        const val CHANNEL_ID = "HEALTH_DATA_CHANNEL"
        const val NOTIFICATION_ID = 1
        const val ACTION_START_MEASUREMENT = "START_MEASUREMENT"
        const val ACTION_STOP_MEASUREMENT = "STOP_MEASUREMENT"
        const val ACTION_REQUEST_STATUS = "REQUEST_STATUS"

        // 브로드캐스트 액션
        const val ACTION_HEART_RATE_UPDATE = "com.ms.wearos.HEART_RATE_UPDATE"
        const val ACTION_SERVICE_STATUS = "com.ms.wearos.SERVICE_STATUS"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val healthServicesClient by lazy { HealthServices.getClient(this) }
    private val measureClient by lazy { healthServicesClient.measureClient }
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private var isCollecting = false
    private var heartRateCallback: MeasureCallback? = null
    private var isCallbackRegistered = false  // 콜백 등록 상태 추가
    private var periodicMeasurementJob: Job? = null
    private var currentHeartRate = 0.0
    private var isEmulator = false  // 에뮬레이터 감지용

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // 에뮬레이터 감지
        isEmulator = isRunningOnEmulator()
        Log.d(TAG, "Service created - Emulator: $isEmulator")
    }

    private fun isRunningOnEmulator(): Boolean {
        Log.d(TAG, "기기정보 확인:")
        Log.d(TAG, "FINGERPRINT: ${android.os.Build.FINGERPRINT}")
        Log.d(TAG, "MODEL: ${android.os.Build.MODEL}")
        Log.d(TAG, "BRAND: ${android.os.Build.BRAND}")
        Log.d(TAG, "DEVICE: ${android.os.Build.DEVICE}")
        Log.d(TAG, "PRODUCT: ${android.os.Build.PRODUCT}")

        val isEmulator = (android.os.Build.FINGERPRINT.startsWith("generic") ||
                android.os.Build.FINGERPRINT.startsWith("unknown") ||
                android.os.Build.MODEL.contains("google_sdk") ||
                android.os.Build.MODEL.contains("Emulator") ||
                android.os.Build.MODEL.contains("Android SDK built for x86") ||
                android.os.Build.MODEL.contains("sdk_gwear") ||
                android.os.Build.PRODUCT.contains("sdk_gwear") ||
                android.os.Build.DEVICE.startsWith("emu") ||
                android.os.Build.BRAND.startsWith("generic") &&
                android.os.Build.DEVICE.startsWith("generic") ||
                "google_sdk" == android.os.Build.PRODUCT)

        Log.d(TAG, "에뮬레이터 여부: $isEmulator")
        return isEmulator
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MEASUREMENT -> startHeartRateCollection()
            ACTION_STOP_MEASUREMENT -> stopHeartRateCollection()
            ACTION_REQUEST_STATUS -> sendServiceStatus()
        }
        return START_STICKY  // 서비스가 죽으면 자동으로 재시작
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
        if (isCollecting) {
            Log.d(TAG, "이미 측정 중")
            sendServiceStatus()
            return
        }

        Log.d(TAG, "심박수 측정 시작 - 에뮬레이터: $isEmulator")

        val notification = createNotification("심박수 측정 준비 중...")
        startForeground(NOTIFICATION_ID, notification)

        isCollecting = true
        saveServiceState(true)

        if (!isEmulator) {
            // 실제 기기에서만 심박수 콜백 설정
            heartRateCallback = object : MeasureCallback {
                private var lastMeasurementTime = 0L  // 마지막 측정 시간 추가

                override fun onAvailabilityChanged(
                    dataType: DeltaDataType<*, *>,
                    availability: Availability
                ) {
                    val statusText = when (availability.toString()) {
                        "AVAILABLE" -> "심박수 측정 가능"
                        "UNAVAILABLE" -> "심박수 측정 불가능"
                        "ACQUIRING" -> "심박수 센서 준비중"
                        "UNAVAILABLE_DEVICE_OFF_BODY" -> "기기가 손목에서 분리됨"
                        "UNAVAILABLE_HIGH_POWER_DISABLED" -> "고전력 모드 비활성화"
                        else -> "상태: $availability"
                    }

                    Log.d(TAG, "센서 상태: $availability")
                    updateNotification(statusText)
                    broadcastServiceStatus(statusText)
                }

                override fun onDataReceived(data: DataPointContainer) {
                    if (!isCollecting) return

                    val currentTime = System.currentTimeMillis()

                    // 10초 간격으로 필터링 (10000ms = 10초)
                    if (currentTime - lastMeasurementTime < 10000) {
                        Log.d(TAG, "10초 미경과 - 건너뜀")
                        return
                    }

                    val heartRateData = data.getData(DataType.HEART_RATE_BPM)
                    heartRateData.forEach { dataPoint ->
                        val bpm = dataPoint.value
                        currentHeartRate = bpm

                        val timestamp = dateFormat.format(Date())

                        // 마지막 측정 시간 업데이트
                        lastMeasurementTime = currentTime

                        // 로그 출력
                        TestLoggingUtils.logHeartRateData(bpm)
                        Log.d(TAG, "실제 심박수: ${bpm.toInt()} BPM ($timestamp)")

                        // 데이터 저장
                        saveHeartRateData(bpm)

                        // 알림 업데이트
                        updateNotification("심박수: ${bpm.toInt()} BPM")

                        // UI 업데이트를 위한 브로드캐스트
                        broadcastHeartRateUpdate(bpm, timestamp)

                        // 비정상 심박수 감지
                        detectHeartRateAnomalies(bpm)
                    }
                }
            }

            // 센서 한 번만 등록
            heartRateCallback?.let { callback ->
                measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
                isCallbackRegistered = true
                Log.d(TAG, "실제 센서 등록 완료 (10초 간격)")
            }
        } else {
            // 에뮬레이터: 10초마다 가짜 데이터 생성
            startPeriodicMeasurement()
        }

        // 서비스 상태 브로드캐스트
        val statusMessage = if (isEmulator) "10초 간격 측정 시작 (테스트 모드)" else "10초 간격 측정 시작"
        broadcastServiceStatus(statusMessage)

        TestLoggingUtils.logMeasurementStart()
    }

    private fun startPeriodicMeasurement() {
        periodicMeasurementJob?.cancel()

        // 에뮬레이터에서만 주기적 실행
        periodicMeasurementJob = serviceScope.launch {
            Log.d(TAG, "에뮬레이터 주기 측정 시작")
            while (isActive && isCollecting) {
                generateFakeHeartRateData()
                delay(10000)  // 10초 대기
            }
        }
    }

    private fun generateFakeHeartRateData() {
        // 60-100 BPM 범위에서 랜덤 심박수 생성 (정상 범위)
        val baseHeartRate = 70.0
        val variation = (-15..15).random()
        val fakeHeartRate = baseHeartRate + variation

        currentHeartRate = fakeHeartRate
        val timestamp = dateFormat.format(Date())

        // 로그 출력
        TestLoggingUtils.logHeartRateData(fakeHeartRate)
        Log.d(TAG, "가짜 심박수: $fakeHeartRate BPM $timestamp")

        // 데이터 저장
        saveHeartRateData(fakeHeartRate)

        // 알림 업데이트
        updateNotification("심박수: ${fakeHeartRate.toInt()} BPM (테스트)")

        // UI 업데이트를 위한 브로드캐스트
        broadcastHeartRateUpdate(fakeHeartRate, timestamp)

        // 비정상 심박수 감지
        detectHeartRateAnomalies(fakeHeartRate)
    }

    private fun stopHeartRateCollection() {
        if (!isCollecting) {
            Log.d(TAG, "측정 중이 아님")
            return
        }

        Log.d(TAG, "심박수 측정 중지")

        isCollecting = false
        saveServiceState(false)

        serviceScope.launch {
            try {
                // 주기적 측정 중지
                periodicMeasurementJob?.cancel()
                periodicMeasurementJob = null

                // 심박수 센서 콜백 해제 (등록되어 있는 경우에만)
                if (isCallbackRegistered && heartRateCallback != null) {
                    try {
                        measureClient.unregisterMeasureCallback(DataType.HEART_RATE_BPM, heartRateCallback!!)
                        Log.d(TAG, "센서 등록 해제 완료")
                    } catch (e: Exception) {
                        Log.w(TAG, "센서 등록 해제 실패", e)
                    } finally {
                        isCallbackRegistered = false
                    }
                }
                heartRateCallback = null

                Log.d(TAG, "센서 정리 완료")
                TestLoggingUtils.logMeasurementStop("심박수")

                // 서비스 상태 브로드캐스트
                broadcastServiceStatus("측정 중지됨")

            } catch (e: Exception) {
                Log.e(TAG, "측정 중지 중 오류", e)
            } finally {
                withContext(Dispatchers.Main) {
                    currentHeartRate = 0.0
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun sendServiceStatus() {
        broadcastServiceStatus(
            if (isCollecting) "10초 간격으로 측정 중" else "측정 중지됨"
        )
    }

    private fun saveServiceState(isRunning: Boolean) {
        val sharedPref = getSharedPreferences("health_settings", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("service_was_running", isRunning)
            apply()
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
            .setContentTitle("심박수")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.splash_icon)
            .setOngoing(true)
            .addAction(R.drawable.splash_icon, "중지", stopPendingIntent)
            .build()
    }

    @SuppressLint("NotificationPermission")
    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun saveHeartRateData(bpm: Double) {
        val timestamp = System.currentTimeMillis()

        // SharedPreferences에 최신 심박수 데이터 저장
        val sharedPref = getSharedPreferences("health_data", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("latest_heart_rate", bpm.toString())
            putLong("latest_heart_rate_timestamp", timestamp)
            apply()
        }
    }

    private fun broadcastHeartRateUpdate(heartRate: Double, timestamp: String) {
        val intent = Intent(ACTION_HEART_RATE_UPDATE).apply {
            putExtra("heart_rate", heartRate)
            putExtra("timestamp", timestamp)
            // 패키지 명시적 설정으로 브로드캐스트 전달 보장
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun broadcastServiceStatus(status: String) {
        val intent = Intent(ACTION_SERVICE_STATUS).apply {
            putExtra("is_running", isCollecting)
            putExtra("status", status)
            // 패키지 명시적 설정으로 브로드캐스트 전달 보장
            setPackage(packageName)
        }
        sendBroadcast(intent)
        Log.d(TAG, "서비스 상태 전송: $status")
    }

    private fun detectHeartRateAnomalies(heartRate: Double) {
        when {
            heartRate > 120 -> {
                val warning = "심박수가 매우 높습니다 (${heartRate.toInt()} BPM)"
                Log.w("HeartRateWarning", warning)
                updateNotification("경고: $warning")
                broadcastServiceStatus("경고: $warning")
            }
            heartRate < 50 && heartRate > 0 -> {
                val warning = "심박수가 매우 낮습니다 (${heartRate.toInt()} BPM)"
                Log.w("HeartRateWarning", warning)
                updateNotification("경고: $warning")
                broadcastServiceStatus("경고: $warning")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "서비스 종료")

        // 정리 작업을 코루틴에서 실행
        serviceScope.launch {
            try {
                // 주기적 측정 중지
                periodicMeasurementJob?.cancel()
                periodicMeasurementJob = null

                // 심박수 센서 콜백 해제 (등록되어 있는 경우에만)
                if (isCallbackRegistered && heartRateCallback != null) {
                    try {
                        measureClient.unregisterMeasureCallback(DataType.HEART_RATE_BPM, heartRateCallback!!)
                        Log.d(TAG, "종료 시 센서 정리 완료y")
                    } catch (e: Exception) {
                        Log.w(TAG, "종료 시 센서 정리 실패", e)
                    } finally {
                        isCallbackRegistered = false
                    }
                }
                heartRateCallback = null

            } catch (e: Exception) {
                Log.e(TAG, "종료 중 오류", e)
            } finally {
                // 마지막에 serviceScope 취소
                serviceScope.cancel()
            }
        }
    }
}

// 서비스 헬퍼 클래스
object HealthServiceHelper {

    fun startService(context: Context) {
        val intent = Intent(context, HealthDataService::class.java).apply {
            action = HealthDataService.ACTION_START_MEASUREMENT
        }
        context.startForegroundService(intent)
        Log.d("HealthServiceHelper", "Start service requested")
    }

    fun stopService(context: Context) {
        val intent = Intent(context, HealthDataService::class.java).apply {
            action = HealthDataService.ACTION_STOP_MEASUREMENT
        }
        context.startService(intent)
        Log.d("HealthServiceHelper", "Stop service requested")
    }

    fun requestServiceStatus(context: Context) {
        val intent = Intent(context, HealthDataService::class.java).apply {
            action = HealthDataService.ACTION_REQUEST_STATUS
        }
        context.startService(intent)
        Log.d("HealthServiceHelper", "Service status requested")
    }
}