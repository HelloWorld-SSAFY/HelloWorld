package com.ms.wearos.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.*
import androidx.health.services.client.unregisterMeasureCallback
import com.ms.helloworld.R
import com.ms.wearos.ui.MainActivity
import kotlinx.coroutines.*
import com.ms.wearos.util.EnhancedStressCalculator
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
        const val ACTION_STRESS_UPDATE = "com.ms.wearos.STRESS_UPDATE"
        const val ACTION_SERVICE_STATUS = "com.ms.wearos.SERVICE_STATUS"
    }

    // 백그라운드 실행을 위한 개선사항
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    private val healthServicesClient by lazy { HealthServices.getClient(this) }
    private val measureClient by lazy { healthServicesClient.measureClient }
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private var isCollecting = false
    private var heartRateCallback: MeasureCallback? = null
    private var isCallbackRegistered = false
    private var periodicMeasurementJob: Job? = null
    private var heartRateMonitoringJob: Job? = null // 추가: 지속적 모니터링
    private var currentHeartRate = 0.0
    private var currentStressIndex = 0
    private var isEmulator = false
    private var lastDataReceivedTime = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Wake Lock 획득 (백그라운드에서도 측정 지속)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HealthDataService::WakeLock"
        )

        // 에뮬레이터 감지
        isEmulator = isRunningOnEmulator()
        Log.d(TAG, "Service created - Emulator: $isEmulator")

        // 개선된 스트레스 계산기 초기화
        EnhancedStressCalculator.setUserInfo(age = 30, restingHeartRate = 65.0)
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
        Log.d(TAG, "onStartCommand 호출: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_MEASUREMENT -> {
                Log.d(TAG, "측정 시작 요청")
                startHeartRateCollection()
            }
            ACTION_STOP_MEASUREMENT -> {
                Log.d(TAG, "측정 중지 요청")
                stopHeartRateCollection()
            }
            ACTION_REQUEST_STATUS -> {
                Log.d(TAG, "상태 요청 - 즉시 응답")
                sendServiceStatus()
            }
            null -> {
                // 재시작된 경우 (시스템에 의해)
                Log.d(TAG, "서비스가 시스템에 의해 재시작됨")
                val sharedPref = getSharedPreferences("heart_rate_prefs", Context.MODE_PRIVATE)
                val wasRunning = sharedPref.getBoolean("service_was_running", false)
                val toggleState = sharedPref.getBoolean("heart_rate_toggle", false)

                Log.d(TAG, "재시작 상태 확인 - wasRunning: $wasRunning, toggleState: $toggleState")

                if (wasRunning || toggleState) {
                    Log.d(TAG, "이전 상태에 따라 측정 재시작")
                    startHeartRateCollection()
                } else {
                    Log.d(TAG, "이전 상태가 중지였으므로 재시작하지 않음")
                    // 상태만 브로드캐스트
                    sendServiceStatus()
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "심박수 및 스트레스 측정",
            NotificationManager.IMPORTANCE_LOW // LOW로 설정하여 배터리 절약
        ).apply {
            description = "실시간 심박수와 스트레스를 측정합니다"
            setShowBadge(false)
            enableVibration(false) // 진동 비활성화로 배터리 절약
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

        Log.d(TAG, "심박수 및 스트레스 측정 시작 - 에뮬레이터: $isEmulator")

        // Wake Lock 활성화 (백그라운드 실행 보장)
        wakeLock?.let {
            if (it.isHeld) {
                Log.d(TAG, "Wake Lock이 이미 활성화됨")
            } else {
                try {
                    it.acquire(10*60*1000L /*10 minutes*/)
                    Log.d(TAG, "Wake Lock 활성화")
                } catch (e: Exception) {
                    Log.e(TAG, "Wake Lock 활성화 실패", e)
                }
            }
        }

        val notification = createNotification("심박수 및 스트레스 측정 준비 중...")
        try {
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "포그라운드 서비스 시작됨")
        } catch (e: Exception) {
            Log.e(TAG, "포그라운드 서비스 시작 실패", e)
            return
        }

        isCollecting = true
        saveServiceState(true)
        lastDataReceivedTime = System.currentTimeMillis()

        // 스트레스 계산기 히스토리 초기화
        EnhancedStressCalculator.clearHistory()

        if (!isEmulator) {
            // 실제 기기에서만 심박수 콜백 설정
            setupRealDeviceMeasurement()
        } else {
            // 에뮬레이터: 10초마다 가짜 데이터 생성
            startPeriodicMeasurement()
        }

        // 백그라운드 모니터링 시작
        startBackgroundMonitoring()

        // 서비스 상태 브로드캐스트
        val statusMessage = if (isEmulator) "10초 간격 측정 시작 (테스트 모드)" else "10초 간격 측정 시작"
        broadcastServiceStatus(statusMessage)

        TestLoggingUtils.logMeasurementStart()
    }

    private fun acquireWakeLock() {
        wakeLock?.let { wl ->
            try {
                if (!wl.isHeld) {
                    wl.acquire(10*60*1000L)
                    Log.d(TAG, "Wake Lock 획득")
                } else {
                    Log.d(TAG, "Wake Lock 이미 보유 중")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Wake Lock 획득 실패", e)
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { wl ->
            try {
                if (wl.isHeld) {
                    wl.release()
                    Log.d(TAG, "Wake Lock 해제")
                } else {
                    Log.d(TAG, "Wake Lock 이미 해제됨")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Wake Lock 해제 실패", e)
            }
        }
    }

    private fun setupRealDeviceMeasurement() {
        heartRateCallback = object : MeasureCallback {
            private var lastMeasurementTime = 0L

            override fun onAvailabilityChanged(
                dataType: DeltaDataType<*, *>,
                availability: Availability
            ) {
                val statusText = when (availability.toString()) {
                    "AVAILABLE" -> "심박수 및 스트레스 측정 가능"
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
                lastDataReceivedTime = currentTime

                // 10초 간격으로 필터링
                if (currentTime - lastMeasurementTime < 10000) {
                    Log.d(TAG, "10초 미경과 - 건너뜀")
                    return
                }

                val heartRateData = data.getData(DataType.HEART_RATE_BPM)
                heartRateData.forEach { dataPoint ->
                    processHeartRateData(dataPoint.value, currentTime)
                    lastMeasurementTime = currentTime
                }
            }
        }

        // 센서 등록
        heartRateCallback?.let { callback ->
            measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
            isCallbackRegistered = true
            Log.d(TAG, "실제 센서 등록 완료 (10초 간격)")
        }
    }

    private fun startPeriodicMeasurement() {
        periodicMeasurementJob?.cancel()

        // 에뮬레이터에서만 주기적 실행
        periodicMeasurementJob = serviceScope.launch {
            Log.d(TAG, "에뮬레이터 주기 측정 시작")
            while (isActive && isCollecting) {
                generateFakeHealthData()
                lastDataReceivedTime = System.currentTimeMillis()
                delay(10000)  // 10초 대기
            }
        }
    }

    // 백그라운드에서도 지속적으로 모니터링
    private fun startBackgroundMonitoring() {
        heartRateMonitoringJob?.cancel()

        heartRateMonitoringJob = serviceScope.launch {
            while (isActive && isCollecting) {
                try {
                    delay(30_000) // 30초마다 체크

                    val currentTime = System.currentTimeMillis()
                    val timeSinceLastData = currentTime - lastDataReceivedTime

                    // 60초 이상 데이터가 없으면 재시작 시도
                    if (timeSinceLastData > 60000) {
                        Log.w(TAG, "데이터 수신 중단 감지 - 센서 재시작 시도")

                        if (!isEmulator) {
                            // 실제 기기에서 센서 재등록
                            restartSensorMeasurement()
                        } else {
                            // 에뮬레이터에서 주기적 측정이 중단된 경우 재시작
                            if (periodicMeasurementJob?.isActive != true) {
                                Log.w(TAG, "에뮬레이터 주기 측정 재시작")
                                startPeriodicMeasurement()
                            }
                        }

                        updateNotification("센서 재시작 중...")
                        broadcastServiceStatus("센서 재시작 중...")
                    }

                    // Wake Lock 갱신
                    if (wakeLock?.isHeld == true) {
                        // Wake Lock 시간 연장
                        releaseWakeLock()
                        acquireWakeLock()
                        Log.d(TAG, "Wake Lock 갱신됨")
                    } else {
                        // Wake Lock이 해제된 경우 다시 획득
                        acquireWakeLock()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "백그라운드 모니터링 중 오류", e)
                }
            }
        }
    }

    private suspend fun restartSensorMeasurement() {
        try {
            // 기존 콜백 해제
            if (isCallbackRegistered && heartRateCallback != null) {
                measureClient.unregisterMeasureCallback(DataType.HEART_RATE_BPM, heartRateCallback!!)
                isCallbackRegistered = false
                Log.d(TAG, "기존 센서 콜백 해제")

                // 잠시 대기 후 재등록
                serviceScope.launch {
                    delay(2000)
                    if (isCollecting) {
                        heartRateCallback?.let { callback ->
                            measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
                            isCallbackRegistered = true
                            Log.d(TAG, "센서 콜백 재등록 완료")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "센서 재시작 실패", e)
        }
    }

    private fun processHeartRateData(bpm: Double, currentTime: Long) {
        currentHeartRate = bpm
        val timestamp = dateFormat.format(Date(currentTime))

        // 개선된 스트레스 지수 계산
        val stressIndex = EnhancedStressCalculator.calculateStressIndex(bpm)
        currentStressIndex = stressIndex
        val stressLevel = EnhancedStressCalculator.getStressLevelText(stressIndex)
        val advice = EnhancedStressCalculator.getStressAdvice(stressIndex)

        // 상세 진단 정보
        val detailedDiagnosis = EnhancedStressCalculator.getDetailedDiagnosis()

        // 개별 로그 출력
        TestLoggingUtils.logHeartRateData(bpm)
        TestLoggingUtils.logStressData(stressIndex, stressLevel, advice)

        // 통합 로그 출력
        TestLoggingUtils.logHealthData(bpm, stressIndex, stressLevel)

        // 상세 진단 정보 로그
        Log.d(TAG, detailedDiagnosis)

        Log.d(TAG, "심박수: ${bpm.toInt()} BPM ($timestamp)")
        Log.d(TAG, "향상된 스트레스 지수: $stressIndex/100 ($stressLevel) ($timestamp)")

        // 데이터 저장
        saveHealthData(bpm, stressIndex)

        // 알림 업데이트
        updateNotification("심박수: ${bpm.toInt()} BPM | 스트레스: $stressLevel")

        // UI 업데이트를 위한 브로드캐스트
        broadcastHeartRateUpdate(bpm, timestamp)
        broadcastStressUpdate(stressIndex, stressLevel, timestamp)

        // 비정상 심박수 및 스트레스 감지
        detectHealthAnomalies(bpm, stressIndex)
    }

    private fun generateFakeHealthData() {
        // 60-100 BPM 범위에서 랜덤 심박수 생성
        val baseHeartRate = 70.0
        val variation = (-15..15).random()
        val fakeHeartRate = baseHeartRate + variation

        processHeartRateData(fakeHeartRate, System.currentTimeMillis())
        Log.d(TAG, "가짜 심박수 생성: $fakeHeartRate BPM (백그라운드 모드)")
    }

    private fun stopHeartRateCollection() {
        if (!isCollecting) {
            Log.d(TAG, "측정 중이 아님")
            return
        }

        Log.d(TAG, "심박수 및 스트레스 측정 중지")

        isCollecting = false
        saveServiceState(false)

        serviceScope.launch {
            try {
                // 백그라운드 모니터링 중지
                heartRateMonitoringJob?.cancel()
                heartRateMonitoringJob = null

                // 주기적 측정 중지
                periodicMeasurementJob?.cancel()
                periodicMeasurementJob = null

                // 심박수 센서 콜백 해제
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

                // Wake Lock 해제
                wakeLock?.let {
                    if (it.isHeld) {
                        it.release()
                        Log.d(TAG, "Wake Lock 해제")
                    }
                }

                // 스트레스 계산기 히스토리 초기화
                EnhancedStressCalculator.clearHistory()

                Log.d(TAG, "센서 정리 완료")
                TestLoggingUtils.logMeasurementStop("심박수 및 스트레스")

                // 서비스 상태 브로드캐스트
                broadcastServiceStatus("측정 중지됨")

            } catch (e: Exception) {
                Log.e(TAG, "측정 중지 중 오류", e)
            } finally {
                withContext(Dispatchers.Main) {
                    currentHeartRate = 0.0
                    currentStressIndex = 0
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun sendServiceStatus() {
        val statusMessage = if (isCollecting) {
            if (isEmulator) "10초 간격으로 측정 중 (테스트 모드)" else "10초 간격으로 측정 중"
        } else {
            "측정 중지됨"
        }

        Log.d(TAG, "서비스 상태 응답 전송: isCollecting=$isCollecting, message=$statusMessage")

        broadcastServiceStatus(statusMessage)
    }

    private fun saveServiceState(isRunning: Boolean) {
        try {
            val sharedPref = getSharedPreferences("heart_rate_prefs", Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putBoolean("service_was_running", isRunning)
                putBoolean("heart_rate_toggle", isRunning)
                putLong("service_last_active", System.currentTimeMillis())
                putBoolean("service_state_saved", true) // 상태 저장 플래그
                apply()
            }
            Log.d(TAG, "서비스 상태 저장 완료: isRunning=$isRunning")
        } catch (e: Exception) {
            Log.e(TAG, "서비스 상태 저장 실패", e)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val stopIntent = Intent(this, HealthDataService::class.java).apply {
            action = ACTION_STOP_MEASUREMENT
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // 메인 액티비티로 이동하는 인텐트 추가
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this, 1, mainIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("건강 모니터링 활성화")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.splash_icon)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(mainPendingIntent) // 알림 클릭 시 앱으로 이동
            .addAction(R.drawable.splash_icon, "중지", stopPendingIntent)
            .setAutoCancel(false) // 자동으로 사라지지 않게
            .build()
    }

    @SuppressLint("NotificationPermission")
    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun saveHealthData(bpm: Double, stressIndex: Int) {
        val timestamp = System.currentTimeMillis()

        // SharedPreferences에 최신 건강 데이터 저장
        val sharedPref = getSharedPreferences("health_data", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("latest_heart_rate", bpm.toString())
            putInt("latest_stress_index", stressIndex)
            putLong("latest_health_timestamp", timestamp)
            apply()
        }
    }

    private fun broadcastHeartRateUpdate(heartRate: Double, timestamp: String) {
        val intent = Intent(ACTION_HEART_RATE_UPDATE).apply {
            putExtra("heart_rate", heartRate)
            putExtra("timestamp", timestamp)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun broadcastStressUpdate(stressIndex: Int, stressLevel: String, timestamp: String) {
        val intent = Intent(ACTION_STRESS_UPDATE).apply {
            putExtra("stress_index", stressIndex)
            putExtra("stress_level", stressLevel)
            putExtra("stress_advice", EnhancedStressCalculator.getStressAdvice(stressIndex))
            putExtra("timestamp", timestamp)
            setPackage(packageName)
        }
        sendBroadcast(intent)
        Log.d(TAG, "스트레스 데이터 브로드캐스트: $stressIndex ($stressLevel)")
    }

    private fun broadcastServiceStatus(status: String) {
        val intent = Intent(ACTION_SERVICE_STATUS).apply {
            putExtra("is_running", isCollecting)
            putExtra("status", status)
            putExtra("timestamp", System.currentTimeMillis()) // 타임스탬프 추가
            setPackage(packageName)
        }
        sendBroadcast(intent)
        Log.d(TAG, "서비스 상태 브로드캐스트: isRunning=$isCollecting, status=$status")
    }

    private fun detectHealthAnomalies(heartRate: Double, stressIndex: Int) {
        var warningMessage = ""

        // 심박수 이상 감지
        when {
            heartRate > 120 -> {
                warningMessage = "심박수가 매우 높습니다 (${heartRate.toInt()} BPM)"
            }
            heartRate < 50 && heartRate > 0 -> {
                warningMessage = "심박수가 매우 낮습니다 (${heartRate.toInt()} BPM)"
            }
        }

        // 스트레스 이상 감지
        when {
            stressIndex >= 80 -> {
                val stressWarning = "스트레스 지수가 매우 높습니다 ($stressIndex/100)"
                warningMessage = if (warningMessage.isEmpty()) stressWarning else "$warningMessage, $stressWarning"
            }
            stressIndex >= 70 -> {
                val stressWarning = "스트레스 지수가 높습니다 ($stressIndex/100)"
                if (warningMessage.isEmpty()) warningMessage = stressWarning
            }
        }

        // 경고 메시지가 있으면 로그 및 알림 업데이트
        if (warningMessage.isNotEmpty()) {
            Log.w("HealthWarning", warningMessage)
            updateNotification("경고: $warningMessage")
            broadcastServiceStatus("경고: $warningMessage")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "서비스 종료")

        // 먼저 수집 중지 플래그 설정
        isCollecting = false
        saveServiceState(false)

        serviceScope.launch {
            try {
                // 백그라운드 모니터링 중지
                heartRateMonitoringJob?.cancel()
                heartRateMonitoringJob = null

                // 주기적 측정 중지
                periodicMeasurementJob?.cancel()
                periodicMeasurementJob = null

                // 심박수 센서 콜백 해제
                if (isCallbackRegistered && heartRateCallback != null) {
                    try {
                        measureClient.unregisterMeasureCallback(DataType.HEART_RATE_BPM, heartRateCallback!!)
                        Log.d(TAG, "종료 시 센서 정리 완료")
                    } catch (e: Exception) {
                        Log.w(TAG, "종료 시 센서 정리 실패", e)
                    } finally {
                        isCallbackRegistered = false
                        heartRateCallback = null
                    }
                }

                // Wake Lock 해제
                releaseWakeLock()

                // 스트레스 계산기 히스토리 초기화
                EnhancedStressCalculator.clearHistory()

            } catch (e: Exception) {
                Log.e(TAG, "종료 중 오류", e)
            } finally {
                withContext(Dispatchers.Main) {
                    // 최종 상태 브로드캐스트
                    broadcastServiceStatus("서비스 종료됨")
                    serviceScope.cancel()
                }
            }
        }
        super.onDestroy()
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