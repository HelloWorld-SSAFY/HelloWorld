package com.ms.wearos.ui

import android.Manifest
import android.app.ActivityManager
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.*
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Face
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelProvider
import com.airbnb.lottie.compose.*
import com.ms.wearos.repository.FcmRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.ms.wearos.ui.theme.HelloWorldTheme
import com.ms.wearos.service.HealthDataService
import com.ms.wearos.service.HealthServiceHelper
import com.ms.wearos.ui.theme.MainColor
import com.ms.wearos.viewmodel.WearMainViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.log

private const val TAG = "싸피_MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    // 화면 상태 관리
    private var currentScreen = mutableStateOf(Screen.Main)

    // UI 상태 관리
    private var currentHeartRate = mutableStateOf(0.0)
    private var isToggleEnabled = mutableStateOf(false)
    private var heartStatus = mutableStateOf("심박수: 준비됨")
    private var lastUpdateTime = mutableStateOf("")

    // 스트레스 상태 변수
    private var currentStressIndex = mutableStateOf(0)
    private var currentStressLevel = mutableStateOf("측정 대기")
    private var stressAdvice = mutableStateOf("")

    // 서버 전송 관련 상태
    private var heartRateJob: kotlinx.coroutines.Job? = null

    // 화면 enum
    enum class Screen {
        Main, HeartRate, FetalMovement, LaborRecord, FetalMovementAnimation
    }

    // 서비스로부터 심박수 데이터를 받기 위한 브로드캐스트 리시버
    private val healthDataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            Log.d(TAG, "브로드캐스트 수신: $action")

            when (action) {
                HealthDataService.ACTION_HEART_RATE_UPDATE -> {
                    val heartRate = intent.getDoubleExtra("heart_rate", 0.0)
                    val timestamp = intent.getStringExtra("timestamp") ?: ""

                    currentHeartRate.value = heartRate
                    lastUpdateTime.value = timestamp

                    Log.d(TAG, "심박수: $heartRate BPM")
                    logCurrentStates("심박수 업데이트")
                }

                HealthDataService.ACTION_STRESS_UPDATE -> {
                    val stressIndex = intent.getIntExtra("stress_index", 0)
                    val stressLevel = intent.getStringExtra("stress_level") ?: "알 수 없음"
                    val advice = intent.getStringExtra("stress_advice") ?: ""
                    val timestamp = intent.getStringExtra("timestamp") ?: ""

                    currentStressIndex.value = stressIndex
                    currentStressLevel.value = stressLevel
                    stressAdvice.value = advice

                    Log.d(TAG, "스트레스 업데이트: $stressIndex ($stressLevel)")
                }

                HealthDataService.ACTION_SERVICE_STATUS -> {
                    val isRunning = intent.getBooleanExtra("is_running", false)
                    val status = intent.getStringExtra("status") ?: ""
                    val timestamp = intent.getLongExtra("timestamp", 0L)


                    Log.d(TAG, "서비스 상태 수신: isRunning=$isRunning, status=$status, timestamp=$timestamp")

                    val previousToggleState = isToggleEnabled.value

                    isToggleEnabled.value = isRunning
                    if (status.isNotEmpty()) {
                        heartStatus.value = status
                    }

                    Log.d(TAG, "UI 상태 변경: $previousToggleState -> $isRunning")
                    logCurrentStates("서비스 상태 업데이트")
                }
            }
        }
    }

    // 권한 요청 런처
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        Log.d(TAG, "모든 권한 승인: $allGranted")
        if (allGranted) {
            checkAndRestoreToggleState()
        }
    }

    private fun logCurrentStates(context: String) {
        Log.d(TAG, "=== 상태 로깅 ($context) ===")
        Log.d(TAG, "UI 토글 상태: ${isToggleEnabled.value}")
        Log.d(TAG, "심박수 상태: ${heartStatus.value}")
        Log.d(TAG, "현재 심박수: ${currentHeartRate.value}")

        val savedToggle = sharedPreferences.getBoolean("heart_rate_toggle", false)
        val serviceWasRunning = sharedPreferences.getBoolean("service_was_running", false)
        val stateSaved = sharedPreferences.getBoolean("service_state_saved", false)

        Log.d(TAG, "저장된 토글: $savedToggle")
        Log.d(TAG, "서비스 실행 기록: $serviceWasRunning")
        Log.d(TAG, "상태 저장 플래그: $stateSaved")
        Log.d(TAG, "=============================")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 시스템 스플래시 제거
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { false }

        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("heart_rate_prefs", Context.MODE_PRIVATE)

        registerBroadcastReceiver()

        checkActualServiceStatus()

        requestHealthPermissions()

        // 토큰 초기화 - 앱 시작 시 자동으로 토큰 요청
        initializeTokens()

        Log.d(TAG, "onCreate 시작")
        logCurrentStates("onCreate")


        setContent {
            var showSplash by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                // 2초 후 스플래시 화면 숨김
                delay(2000)
                showSplash = false
            }

            if (showSplash) {
                CustomSplashScreen() // 여기서 사용
            } else {
                AppContent() // 메인 앱 화면
            }
        }

        // 토큰 상태 로깅 시작
        lifecycleScope.launch {
            delay(1000)
            val viewModel: WearMainViewModel =
                ViewModelProvider(this@MainActivity)[WearMainViewModel::class.java]
            logTokenStatus(viewModel)
        }
    }

    /**
     * 토큰 초기화 처리를 하나의 함수로 통합
     */
    private fun initializeTokens() {
        lifecycleScope.launch {
            delay(1000) // 앱 초기화 완료 후 실행

            val viewModel: WearMainViewModel =
                ViewModelProvider(this@MainActivity)[WearMainViewModel::class.java]

            // 토큰 상태 초기화 및 필요시 핸드폰에 토큰 요청
            viewModel.initializeTokenState(this@MainActivity)

            // 토큰 상태 모니터링 시작
            monitorTokenStatus(viewModel)
        }
    }

    /**
     * 토큰 상태를 지속적으로 모니터링
     */
    private fun monitorTokenStatus(viewModel: WearMainViewModel) {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                Log.d(TAG, "인증 상태: ${state.isAuthenticated}")
                if (state.isAuthenticated) {
                    Log.d(TAG, "토큰 사용 가능 - 건강 데이터 전송 준비됨")
                } else {
                    Log.d(TAG, "토큰 없음 - 핸드폰 연결 및 로그인 필요")
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 1초 지연 후 상태 확인 (브로드캐스트 리시버 등록 완료 대기)
        lifecycleScope.launch {
            delay(1000)
            recheckServiceStatus()
        }
    }

    // 새로운 함수: 서비스 상태 재확인
    private fun recheckServiceStatus() {
        val savedToggleState = sharedPreferences.getBoolean("heart_rate_toggle", false)
        val currentUiState = isToggleEnabled.value

        Log.d(TAG, "상태 재확인 - 저장된 상태: $savedToggleState, UI 상태: $currentUiState")

        if (savedToggleState != currentUiState) {
            Log.w(TAG, "상태 불일치 감지 - 재동기화 필요")
            checkServiceWithPing()
        } else if (savedToggleState) {
            // 저장된 상태가 활성화인 경우 서비스 핑으로 재확인
            Log.d(TAG, "저장된 상태가 활성화 - 서비스 실제 상태 확인")
            HealthServiceHelper.requestServiceStatus(this)
        }
    }

    private fun checkActualServiceStatus() {
        val savedToggleState = sharedPreferences.getBoolean("heart_rate_toggle", false)
        val serviceWasRunning = sharedPreferences.getBoolean("service_was_running", false)
        val appDestroyedAt = sharedPreferences.getLong("app_destroyed_at", 0L)
        val currentTime = System.currentTimeMillis()

        Log.d(TAG, "서비스 상태 확인 - 저장된 토글: $savedToggleState, 서비스 실행 기록: $serviceWasRunning")

        // 앱이 최근에 종료되었는지 확인 (예: 30초 이내)
        val wasRecentlyDestroyed = appDestroyedAt > 0 && (currentTime - appDestroyedAt) < 30000

        if (wasRecentlyDestroyed) {
            Log.d(TAG, "앱이 최근에 종료되어 서비스도 중지되었을 가능성이 높음")
            // 프로세스 종료로 인한 재시작이므로 상태 초기화
            resetToStoppedState()
            return
        }


        // 앱 프로세스가 죽었다가 살아났을 때를 고려
        if (savedToggleState || serviceWasRunning) {
            Log.d(TAG, "이전에 서비스가 실행 중이었음 - 현재 상태 확인")

            // UI를 일단 로딩 상태로 설정
            isToggleEnabled.value = savedToggleState
            heartStatus.value = if (savedToggleState) "서비스 상태 확인 중..." else "측정 중지됨"

            // 실제 서비스 상태 확인
            if (savedToggleState) {
                // 1초 후 핑 확인 (브로드캐스트 리시버 등록 완료 대기)
                lifecycleScope.launch {
                    delay(1000)
                    checkServiceWithPing()
                }
            } else {
                resetHealthData()
            }
        } else {
            Log.d(TAG, "이전에 서비스가 중지 상태였음")
            isToggleEnabled.value = false
            heartStatus.value = "측정 중지됨"
            resetHealthData()
            resetToStoppedState()
        }
    }

    // 새로운 함수 추가
    private fun resetToStoppedState() {
        Log.d(TAG, "상태를 중지 상태로 초기화")
        isToggleEnabled.value = false
        heartStatus.value = "측정 중지됨"
        resetHealthData()

        // SharedPreferences도 초기화
        sharedPreferences.edit()
            .putBoolean("heart_rate_toggle", false)
            .putBoolean("service_was_running", false)
            .remove("app_destroyed_at")
            .apply()
    }

    private fun checkServiceWithPing() {
        var responseReceived = false
        var tempReceiver: BroadcastReceiver? = null

        Log.d(TAG, "서비스 핑 시작")

        tempReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == HealthDataService.ACTION_SERVICE_STATUS) {
                    responseReceived = true
                    val isRunning = intent.getBooleanExtra("is_running", false)

                    Log.d(TAG, "서비스 핑 응답: isRunning=$isRunning")

                    lifecycleScope.launch {
                        if (isRunning) {
                            Log.d(TAG, "서비스 실행 중 - UI 상태 동기화")
                            isToggleEnabled.value = true
                            heartStatus.value = "측정 중..."

                            // SharedPreferences도 업데이트
                            sharedPreferences.edit()
                                .putBoolean("heart_rate_toggle", true)
                                .apply()
                        } else {
                            Log.d(TAG, "서비스 중지됨 - UI 상태 동기화")
                            isToggleEnabled.value = false
                            heartStatus.value = "측정 중지됨"
                            resetHealthData()

                            // SharedPreferences 업데이트
                            sharedPreferences.edit()
                                .putBoolean("heart_rate_toggle", false)
                                .putBoolean("service_was_running", false)
                                .apply()
                        }

                        // 임시 리시버 해제
                        tempReceiver?.let { receiver ->
                            try {
                                unregisterReceiver(receiver)
                                Log.d(TAG, "임시 리시버 해제 완료")
                            } catch (e: Exception) {
                                Log.w(TAG, "임시 리시버 해제 실패", e)
                            }
                        }
                    }
                }
            }
        }

        try {
            val tempFilter = IntentFilter(HealthDataService.ACTION_SERVICE_STATUS)
            ContextCompat.registerReceiver(
                this,
                tempReceiver,
                tempFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            // 서비스에 상태 요청
            HealthServiceHelper.requestServiceStatus(this)
            Log.d(TAG, "서비스 상태 요청 전송")

            // 3초 후에도 응답이 없으면 서비스 중지된 것으로 판단
            lifecycleScope.launch {
                delay(3000) // 2초에서 3초로 증가

                if (!responseReceived) {
                    Log.w(TAG, "서비스 핑 타임아웃 - 서비스 중지된 것으로 판단")

                    isToggleEnabled.value = false
                    heartStatus.value = "측정 중지됨"
                    resetHealthData()

                    sharedPreferences.edit()
                        .putBoolean("heart_rate_toggle", false)
                        .putBoolean("service_was_running", false)
                        .apply()

                    tempReceiver?.let { receiver ->
                        try {
                            unregisterReceiver(receiver)
                            Log.d(TAG, "임시 리시버 해제 완료 (타임아웃)")
                        } catch (e: Exception) {
                            Log.w(TAG, "임시 리시버 해제 실패 (타임아웃)", e)
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "서비스 핑 실행 실패", e)
            // 실패 시 안전하게 UI 상태 초기화
            isToggleEnabled.value = false
            heartStatus.value = "서비스 상태 확인 실패"
            resetHealthData()
        }
    }

    // 건강 데이터 초기화 함수 추가
    private fun resetHealthData() {
        currentHeartRate.value = 0.0
        currentStressIndex.value = 0
        currentStressLevel.value = "측정 대기"
        stressAdvice.value = ""
        lastUpdateTime.value = ""
    }

    // 새 함수 추가: 서비스 실행 상태 확인
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy 호출")
        logCurrentStates("onDestroy")

        // 현재 상태를 한 번 더 저장
        sharedPreferences.edit()
            .putBoolean("heart_rate_toggle", isToggleEnabled.value)
            .putBoolean("service_was_running", isToggleEnabled.value)
            .putLong("app_destroyed_at", System.currentTimeMillis())
            .apply()

        Log.d(TAG, "앱 종료 시 상태 저장 완료")

        super.onDestroy()

        heartRateJob?.cancel()
        try {
            unregisterReceiver(healthDataReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "수신기 등록 해제 오류", e)
        }
    }

    private fun registerBroadcastReceiver() {
        val filter = IntentFilter().apply {
            addAction(HealthDataService.ACTION_HEART_RATE_UPDATE)
            addAction(HealthDataService.ACTION_STRESS_UPDATE)
            addAction(HealthDataService.ACTION_SERVICE_STATUS)
        }
        // 항상 NOT_EXPORTED (앱 내부 전용 브로드캐스트)
        ContextCompat.registerReceiver(
            this,
            healthDataReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun checkAndRestoreToggleState() {
        if (isToggleEnabled.value) {
            Log.d(TAG, "토글 상태 복원 - 서비스 시작 서비스")
            HealthServiceHelper.startService(this)
        }
    }

    private fun logTokenStatus(viewModel: WearMainViewModel) {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                Log.d(TAG, "인증 상태가 변경되었습니다: ${state.isAuthenticated}")
                if (state.isAuthenticated) {
                    Log.d(TAG, "Access 토큰 있음")
                } else {
                    Log.d(TAG, "Access 토큰 없음")
                }
            }
        }
    }

    private fun requestHealthPermissions() {
        val toRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            toRequest += Manifest.permission.BODY_SENSORS
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            toRequest += Manifest.permission.POST_NOTIFICATIONS
        }

        if (toRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(toRequest.toTypedArray())
        } else {
            checkAndRestoreToggleState()
        }
    }

    private suspend fun ensureAuthAndCouple(
        viewModel: WearMainViewModel,
        onAuthenticated: () -> Unit,
        featureName: String = "이 기능"
    ): Boolean {
        if (!viewModel.uiState.value.isAuthenticated) {
            // 토큰 요청 시도
            viewModel.requestTokenFromPhone(this)
            delay(2000) // 잠시 대기

            // 다시 확인
            if (!viewModel.uiState.value.isAuthenticated) {
                Toast.makeText(this, "모바일 앱에서 로그인해주세요.", Toast.LENGTH_LONG).show()
                return false
            }
        }

        val tokenStatus = viewModel.checkTokenStatus()

        // 토큰 확인
        if (!viewModel.uiState.value.isAuthenticated) {
            val msg = when {
                tokenStatus.contains("토큰 없음") -> "모바일 앱에서 로그인해주세요."
                tokenStatus.contains("토큰 만료됨") -> "토큰이 만료되었습니다. 모바일 앱에서 다시 로그인해주세요."
                else -> "모바일 로그인이 필요합니다."
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            return false
        }

        // 커플ID 확인 (네트워크 호출, suspend)
        val coupleId = viewModel.getCoupleIdIfValid()
        if (coupleId == null) {
            Toast.makeText(
                this,
                "커플 연동이 필요합니다. 모바일 앱에서 커플 등록을 완료해주세요.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        Log.d(TAG, "인증+커플 확인됨 - $featureName 사용 가능 (coupleId=$coupleId)")
        onAuthenticated()
        return true
    }


    // 10초 간격 심박수 서버 전송 함수
    private fun startHeartRateServerSync(viewModel: WearMainViewModel) {
        heartRateJob?.cancel()
        heartRateJob = lifecycleScope.launch {
            while (isToggleEnabled.value) {
                val uiState = viewModel.uiState.value

                if (uiState.isAuthenticated) {
                    val heartRate = currentHeartRate.value
                    val stressIndex = currentStressIndex.value

                    // 현재 시간을 ISO 8601 형식으로 생성
                    val currentTime = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Seoul"))
                        .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)

                    Log.d(
                        TAG,
                        "서버로 건강 데이터 전송: HeartRate=$heartRate BPM, Stress=$stressIndex, Time=$currentTime"
                    )

                    // 심박수와 스트레스 지수를 함께 전송
                    viewModel.sendHealthData(
                        date = currentTime,
                        heartRate = if (heartRate > 0) heartRate.toInt() else 0,
                        stress = stressIndex
                    )
                } else {
                    Log.w(TAG, "심박수 서버 전송 실패 - 인증 필요")
                }
                delay(2000) // 10초 대기
            }
        }
    }

    // 심박수 측정 토글 (인증 체크 포함)
    private fun toggleHeartRateMeasurement(enabled: Boolean, viewModel: WearMainViewModel) {
        Log.d(TAG, "심박수 토글 요청: $enabled")
        logCurrentStates("토글 요청 전")

        if (enabled) {
            // 심박수 측정 시작 시 인증 체크
            lifecycleScope.launch {
                ensureAuthAndCouple(
                    viewModel = viewModel,
                    onAuthenticated = {
                        lifecycleScope.launch {
                            // 첫 실행 체크
                            val isFirstRun = checkIfFirstRun()

                            if (isFirstRun) {
                                Log.d(TAG, "첫 실행 감지 - 추가 초기화 수행")
                                handleFirstRunSetup()
                            }

                            // 먼저 상태 업데이트
                            isToggleEnabled.value = true

                            sharedPreferences.edit()
                                .putBoolean("heart_rate_toggle", enabled)
                                .apply()

                            Log.d(TAG, "Starting heart rate service with server sync")

                            if (isFirstRun) {
                                delay(1000)
                            }

                            HealthServiceHelper.startService(this@MainActivity)
                            heartStatus.value = "서비스 시작 중..."

                            // 서버 동기화 시작
                            startHeartRateServerSync(viewModel)

                            // 첫 실행 플래그 업데이트
                                if (isFirstRun) {
                                    sharedPreferences.edit()
                                        .putBoolean("first_run_completed", true)
                                        .apply()
                                }
                        }
                    },
                    featureName = "심박수 서버 연동"
                )
            }
        } else {
            // 측정 중지는 인증 없이 가능
            lifecycleScope.launch {
                sharedPreferences.edit()
                    .putBoolean("heart_rate_toggle", false)
                    .apply()

                Log.d(TAG, "Stopping heart rate service")
                HealthServiceHelper.stopService(this@MainActivity)

                // 서버 동기화 중지
                heartRateJob?.cancel()

                currentHeartRate.value = 0.0
                currentStressIndex.value = 0
                currentStressLevel.value = "측정 대기"
                stressAdvice.value = ""
                lastUpdateTime.value = ""
                heartStatus.value = "측정 중지됨"
                isToggleEnabled.value = false
            }
        }
    }

    // 첫 실행 체크
    private fun checkIfFirstRun(): Boolean {
        return !sharedPreferences.getBoolean("first_run_completed", false)
    }

    // 첫 실행 설정
    private suspend fun handleFirstRunSetup() {
        try {
            Log.d(TAG, "첫 실행 설정 수행")

            // 1. 배터리 최적화 예외 요청 (가능한 경우)
            requestBatteryOptimizationExemption()

            // 2. 추가 권한 재확인
            ensureAllPermissions()

            // 3. 서비스 초기화를 위한 추가 대기
            delay(2000)

            Log.d(TAG, "첫 실행 설정 완료")
        } catch (e: Exception) {
            Log.e(TAG, "첫 실행 설정 실패", e)
        }
    }

    // 배터리 최적화 예외 요청 (Wear OS에서 가능한 경우)
    private fun requestBatteryOptimizationExemption() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                val packageName = packageName

                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    Log.d(TAG, "배터리 최적화 예외 필요")
                    // Wear OS에서는 자동으로 예외 처리되는 경우가 많음
                } else {
                    Log.d(TAG, "배터리 최적화 예외 이미 적용됨")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "배터리 최적화 체크 실패", e)
        }
    }

    // 모든 권한 재확인
    private fun ensureAllPermissions() {
        val missingPermissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            missingPermissions.add(Manifest.permission.BODY_SENSORS)
        }

        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            missingPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (missingPermissions.isNotEmpty()) {
            Log.w(TAG, "누락된 권한 발견: $missingPermissions")
        } else {
            Log.d(TAG, "모든 권한 확인됨")
        }
    }

    // 태동 기록 (인증 체크 포함)
    private suspend fun recordFetalMovement(viewModel: WearMainViewModel): Boolean {
        return ensureAuthAndCouple(
            viewModel = viewModel,
            onAuthenticated = {
                // 수정: ISO 8601 형식으로 변경 (서버가 기대하는 형식)
                val currentTime = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Seoul"))
                    .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                // 결과: "2025-09-21T11:30:45.123Z" 형식

                Log.d(TAG, "태동 기록됨: $currentTime")
                viewModel.sendFetalMovementData(currentTime)
            },
            featureName = "태동 기록 서버 전송"
        )
    }

    // 진통 기록 (인증 체크 포함)
    private suspend fun recordLaborData(
        viewModel: WearMainViewModel,
        startTime: String,
        endTime: String
    ): Boolean {
        return ensureAuthAndCouple(
            viewModel = viewModel,
            onAuthenticated = {
                Log.d(TAG, "서버로 진통 데이터 전송 - 시작: $startTime, 종료: $endTime")
                viewModel.sendLaborData(startTime, endTime)
            },
            featureName = "진통 기록 서버 전송"
        )
    }

    @Composable
    fun AppContent() {
        HelloWorldTheme {
            val viewModel: WearMainViewModel = hiltViewModel()

            // 에러 및 성공 메시지 처리 추가
            val uiState by viewModel.uiState.collectAsState()

            LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
                uiState.errorMessage?.let { error ->
                    Log.e(TAG, "UI Error: $error")
                    Toast.makeText(
                        this@MainActivity,
                        error,
                        Toast.LENGTH_LONG
                    ).show()
                    viewModel.clearError()
                }

                uiState.successMessage?.let { success ->
                    Log.i(TAG, "UI Success: $success")
                    Toast.makeText(
                        this@MainActivity,
                        success,
                        Toast.LENGTH_SHORT
                    ).show()
                    viewModel.clearSuccess()
                }
            }

            val screen by currentScreen
            when (screen) {
                Screen.Main -> MainScreen()
                Screen.HeartRate -> HeartRateScreen(viewModel)
                Screen.FetalMovement -> FetalMovementScreen(viewModel)
                Screen.LaborRecord -> LaborRecordScreen(viewModel)
                Screen.FetalMovementAnimation -> FetalMovementAnimationScreen(viewModel)
            }
        }
    }

    @Composable
    fun MainScreen() {
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "임신 케어",
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center
            )

            MenuCard(
                title = "심박수",
                icon = Icons.Default.Favorite,
                description = "심박수 측정",
                onClick = { currentScreen.value = Screen.HeartRate }
            )

            MenuCard(
                title = "태동 기록",
                icon = Icons.Default.Face,
                description = "태동 기록하기",
                onClick = { currentScreen.value = Screen.FetalMovement }
            )

            MenuCard(
                title = "진통 기록",
                icon = Icons.Default.MonitorHeart,
                description = "진통 간격 기록",
                onClick = { currentScreen.value = Screen.LaborRecord }
            )
        }
    }

    @Composable
    fun MenuCard(
        title: String,
        icon: ImageVector,
        description: String,
        onClick: () -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colors.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onBackground
                )
            }
        }
    }

    @Composable
    fun HeartRateScreen(viewModel: WearMainViewModel) {
        val scrollState = rememberScrollState()

        BackHandler {
            currentScreen.value = Screen.Main
        }

        val heartRate by currentHeartRate
        val toggleEnabled by isToggleEnabled

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "심박수",
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = {}
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${heartRate.toInt()} BPM",
                        style = MaterialTheme.typography.title1,
                        color = if (toggleEnabled && heartRate > 0) Color.Red else MaterialTheme.colors.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ToggleButton(
                        checked = toggleEnabled,
                        onCheckedChange = { enabled ->
                            toggleHeartRateMeasurement(enabled, viewModel)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (toggleEnabled) "측정종료" else "측정시작",
                                style = MaterialTheme.typography.caption2
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun FetalMovementScreen(viewModel: WearMainViewModel) {
        var fetalMovementCount by remember { mutableStateOf(0) }

        BackHandler {
            currentScreen.value = Screen.Main
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "태동 기록",
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center
            )

            Icon(
                imageVector = Icons.Default.Face,
                contentDescription = "태동",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colors.primary
            )

            Button(
                onClick = {
                    // 애니메이션 화면으로 이동
                    currentScreen.value = Screen.FetalMovementAnimation
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(
                    text = "태동 기록하기",
                    style = MaterialTheme.typography.button,
                    color = Color.White
                )
            }
        }
    }

    @Composable
    fun LaborRecordScreen(viewModel: WearMainViewModel) {
        var isLaborActive by remember { mutableStateOf(false) }
        var laborStartTime by remember { mutableStateOf<String?>(null) } // String으로 변경
        var laborCount by remember { mutableStateOf(0) }
        var lastLaborEndTime by remember { mutableStateOf("") }

        BackHandler {
            currentScreen.value = Screen.Main
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "진통 기록",
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center
            )

            Icon(
                imageVector = Icons.Default.MonitorHeart,
                contentDescription = "진통",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colors.primary
            )

            Button(
                onClick = {
                    if (!isLaborActive) {
                        // 진통 시작 - 시작 시간만 저장
                        val startTime =
                            java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Seoul"))
                                .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        laborStartTime = startTime
                        isLaborActive = true

                        Log.d(TAG, "진통 시작됨: $startTime")
                    } else {
                        // 진통 종료 - 서버로 데이터 전송
                        val endTime = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Seoul"))
                            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        val startTime = laborStartTime

                        if (startTime != null) {
                            lifecycleScope.launch {
                                val success = recordLaborData(viewModel, startTime, endTime)
                                if (success) {
                                    isLaborActive = false
                                    laborCount++
                                    lastLaborEndTime = java.text.SimpleDateFormat(
                                        "HH:mm:ss",
                                        java.util.Locale.getDefault()
                                    )
                                        .format(java.util.Date())
                                    laborStartTime = null
                                }
                            }
                            Log.d(TAG, "진통 종료됨: $endTime")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (isLaborActive) Color(0xFFF49699) else MaterialTheme.colors.primary
                )
            ) {
                Text(
                    text = if (isLaborActive) "진통 종료" else "진통 시작",
                    style = MaterialTheme.typography.button,
                    color = Color.White
                )
            }

            if (laborCount > 0 || isLaborActive) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {}
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "진통 기록",
                            style = MaterialTheme.typography.caption1,
                            color = MaterialTheme.colors.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (isLaborActive) "진통 중..." else "진통 대기 중",
                            style = MaterialTheme.typography.body2,
                            color = if (isLaborActive) Color.Red else MaterialTheme.colors.onSurface
                        )

                        if (laborCount > 0) {
                            Text(
                                text = "총 진통 횟수: ${laborCount}회",
                                style = MaterialTheme.typography.caption2,
                                color = MaterialTheme.colors.onSurface
                            )
                        }

                        if (lastLaborEndTime.isNotEmpty() && !isLaborActive) {
                            Text(
                                text = "마지막 종료: $lastLaborEndTime",
                                style = MaterialTheme.typography.caption2,
                                color = MaterialTheme.colors.onSurface
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun FetalMovementAnimationScreen(viewModel: WearMainViewModel) {
        var fetalMovementCount by remember { mutableStateOf(0) }
        var isRecording by remember { mutableStateOf(false) }

        BackHandler {
            currentScreen.value = Screen.FetalMovement
        }

        LaunchedEffect(Unit) {
            // 화면이 로드되면 자동으로 태동 기록 시작
            isRecording = true

            // 애니메이션 시간 (3초) 후에 태동 기록 실행
            delay(3000)

            if (recordFetalMovement(viewModel)) {
                fetalMovementCount++
                Toast.makeText(
                    this@MainActivity,
                    "태동이 기록되었습니다",
                    Toast.LENGTH_SHORT
                ).show()

                // 1초 후 이전 화면으로 돌아가기
                delay(1000)
                currentScreen.value = Screen.FetalMovement
            } else {
                // 실패 시에도 이전 화면으로 돌아가기
                delay(1000)
                currentScreen.value = Screen.FetalMovement
            }
        }

        if (isRecording) {
            // Raw 폴더의 Lottie 애니메이션 사용
            val composition by rememberLottieComposition(
                LottieCompositionSpec.RawRes(com.ms.helloworld.R.raw.baby_kick_lottie)
            )
            val progress by animateLottieCompositionAsState(
                composition = composition,
                iterations = LottieConstants.IterateForever
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = Color(0xFFDDE0E7)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 애니메이션이 텍스트 위 공간을 모두 차지
                LottieAnimation(
                    composition = composition,
                    progress = { progress },
                    modifier = Modifier
                        .weight(1f)  // 남은 공간을 모두 차지
                        .padding(start = 65.dp, top = 10.dp)
                        .fillMaxWidth(),
                )

                // 텍스트는 하단에 고정
                Text(
                    text = "태동 기록 중...",
                    style = MaterialTheme.typography.title3,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(bottom = 24.dp)
                )
            }
        }
    }
}