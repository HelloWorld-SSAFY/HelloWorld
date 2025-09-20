package com.ms.wearos.ui

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
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
import com.ms.wearos.repository.FcmRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.ms.wearos.ui.theme.HelloWorldTheme
import com.ms.wearos.service.HealthDataService
import com.ms.wearos.service.HealthServiceHelper
import com.ms.wearos.viewmodel.WearMainViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

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
        Main, HeartRate, FetalMovement, LaborRecord
    }

    // 서비스로부터 심박수 데이터를 받기 위한 브로드캐스트 리시버
    private val healthDataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                HealthDataService.ACTION_HEART_RATE_UPDATE -> {
                    val heartRate = intent.getDoubleExtra("heart_rate", 0.0)
                    val timestamp = intent.getStringExtra("timestamp") ?: ""

                    currentHeartRate.value = heartRate
                    lastUpdateTime.value = timestamp

                    Log.d(TAG, "심박수: $heartRate BPM")
                }

                HealthDataService.ACTION_STRESS_UPDATE -> {
                    val stressIndex = intent.getIntExtra("stress_index", 0)
                    val stressLevel = intent.getStringExtra("stress_level") ?: "알 수 없음"
                    val advice = intent.getStringExtra("stress_advice") ?: ""
                    val timestamp = intent.getStringExtra("timestamp") ?: ""

                    currentStressIndex.value = stressIndex
                    currentStressLevel.value = stressLevel
                    stressAdvice.value = advice

                    Log.d(TAG, "스트레스 지수: $stressIndex ($stressLevel)")
                    Log.d(TAG, "스트레스 advice: $advice")
                }

                HealthDataService.ACTION_SERVICE_STATUS -> {
                    val isRunning = intent.getBooleanExtra("is_running", false)
                    val status = intent.getStringExtra("status") ?: ""

                    isToggleEnabled.value = isRunning
                    if (status.isNotEmpty()) {
                        heartStatus.value = status
                    }

                    Log.d(TAG, "서비스 상태 업데이트: $isRunning")
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

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("heart_rate_prefs", Context.MODE_PRIVATE)
        isToggleEnabled.value = sharedPreferences.getBoolean("heart_rate_toggle", false)

        requestHealthPermissions()
        registerBroadcastReceiver()

        setContent {
            AppContent()
        }

        // 토큰 상태 로깅 시작
        lifecycleScope.launch {
            delay(1000) // 잠시 대기 후 시작
            val viewModel: WearMainViewModel =
                ViewModelProvider(this@MainActivity)[WearMainViewModel::class.java]
            logTokenStatus(viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        HealthServiceHelper.requestServiceStatus(this)
    }

    override fun onDestroy() {
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
        registerReceiver(healthDataReceiver, filter, RECEIVER_NOT_EXPORTED)
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
        val permissions = arrayOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.FOREGROUND_SERVICE_HEALTH
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            checkAndRestoreToggleState()
        }
    }

    private suspend fun ensureAuthAndCouple(
        viewModel: WearMainViewModel,
        onAuthenticated: () -> Unit,
        featureName: String = "이 기능"
    ): Boolean {
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
                    // 커플 아이디 조회
                    val coupleId = viewModel.getCoupleIdIfValid()
                    if (coupleId != null) {
                        val heartRate = currentHeartRate.value
                        val stressIndex = currentStressIndex.value

                        // 현재 시간을 ISO 8601 형식으로 생성
                        val currentTime = java.time.Instant.now().toString()

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
                        Log.w(TAG, "건강 데이터 서버 전송 실패 - 커플 아이디 조회 실패")
                    }
                } else {
                    Log.w(TAG, "심박수 서버 전송 실패 - 인증 필요")
                }
                delay(10000) // 10초 대기
            }
        }
    }

    // 심박수 측정 토글 (인증 체크 포함)
    private fun toggleHeartRateMeasurement(enabled: Boolean, viewModel: WearMainViewModel) {
        if (enabled) {
            // 심박수 측정 시작 시 인증 체크
            lifecycleScope.launch {
                ensureAuthAndCouple(
                    viewModel = viewModel,
                    onAuthenticated = {
                        lifecycleScope.launch {
                            sharedPreferences.edit()
                                .putBoolean("heart_rate_toggle", enabled)
                                .apply()

                            Log.d(TAG, "Starting heart rate service with server sync")
                            HealthServiceHelper.startService(this@MainActivity)
                            heartStatus.value = "서비스 시작 중..."

                            // 서버 동기화 시작
                            startHeartRateServerSync(viewModel)
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

    // 태동 기록 (인증 체크 포함)
    private suspend fun recordFetalMovement(viewModel: WearMainViewModel): Boolean {
        return ensureAuthAndCouple(
            viewModel = viewModel,
            onAuthenticated = {
                val currentTime =
                    java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date())

                Log.d(TAG, "태동 기록됨: $currentTime")
//                viewModel.sendFetalMovementData(currentTime)
            },
            featureName = "태동 기록 서버 전송"
        )
    }

    // 진통 기록 (인증 체크 포함)
    private suspend fun recordLaborData(
        viewModel: WearMainViewModel,
        isActive: Boolean,
        duration: String? = null,
        interval: String? = null
    ): Boolean {
        return ensureAuthAndCouple(
            viewModel = viewModel,
            onAuthenticated = {
                Log.d(TAG, "서버로 진통 데이터 전송 - 활성: $isActive, 지속시간: $duration, 간격: $interval")
//                viewModel.sendLaborData(isActive, duration, interval)
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
                                text = if (toggleEnabled) "측정중" else "측정시작",
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

            if (fetalMovementCount > 0) {
                Text(
                    text = "오늘 태동 횟수: ${fetalMovementCount}회",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onBackground
                )
            }

            Button(
                onClick = {
                    lifecycleScope.launch {
                        if (recordFetalMovement(viewModel)) {
                            fetalMovementCount++
                        }
                    }
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
        var laborStartTime by remember { mutableStateOf<Long?>(null) }
        var laborDuration by remember { mutableStateOf("") }
        var laborCount by remember { mutableStateOf(0) }
        var lastLaborEndTime by remember { mutableStateOf("") }
        var laborInterval by remember { mutableStateOf("") }
        var previousLaborEndTime by remember { mutableStateOf<Long?>(null) }

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
                    val currentTimeMs = System.currentTimeMillis()
                    val currentTimeString =
                        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                            .format(java.util.Date(currentTimeMs))

                    if (!isLaborActive) {
                        // 진통 시작
                        var intervalToSend: String? = null

                        if (previousLaborEndTime != null) {
                            val intervalMs = currentTimeMs - previousLaborEndTime!!
                            val intervalMinutes = intervalMs / (1000 * 60)
                            val intervalSeconds = (intervalMs % (1000 * 60)) / 1000
                            intervalToSend = "${intervalMinutes}분 ${intervalSeconds}초"
                            laborInterval = intervalToSend

                            Log.d(TAG, "진통 시작됨: $currentTimeString, 이전 진통과의 간격: $intervalToSend")
                        } else {
                            Log.d(TAG, "첫 번째 진통 시작됨: $currentTimeString")
                        }

                        var success = false
                        // 서버로 진통 시작 데이터 전송 (인증 체크 포함)
                        lifecycleScope.launch {
                            success = recordLaborData(viewModel, true, null, intervalToSend)
                        }

                        if (success) {
                            isLaborActive = true
                            laborStartTime = currentTimeMs
                            laborDuration = ""
                        }

                        if (previousLaborEndTime != null) {
                            val intervalMs = currentTimeMs - previousLaborEndTime!!
                            val intervalMinutes = intervalMs / (1000 * 60)
                            val intervalSeconds = (intervalMs % (1000 * 60)) / 1000
                            laborInterval = "${intervalMinutes}분 ${intervalSeconds}초"

                            Log.d(TAG, "진통 시작됨: $currentTimeString, 이전 진통과의 간격: $laborInterval")
                        } else {
                            Log.d(TAG, "첫 번째 진통 시작됨: $currentTimeString")
                        }

                    } else {
                        // 진통 종료
                        var durationToSend = ""

                        if (laborStartTime != null) {
                            val durationMs = currentTimeMs - laborStartTime!!
                            val durationMinutes = durationMs / (1000 * 60)
                            val durationSeconds = (durationMs % (1000 * 60)) / 1000
                            durationToSend = "${durationMinutes}분 ${durationSeconds}초"

                            Log.d(
                                TAG,
                                "진통 종료됨: $currentTimeString, 지속시간: $laborDuration, 총 횟수: $laborCount"
                            )
                        }

                        // 서버로 진통 종료 데이터 전송 (인증 체크 포함)
                        var success = false
                        lifecycleScope.launch {
                            success = recordLaborData(viewModel, false, durationToSend, null)
                        }

                        // 인증이 성공한 경우에만 상태 변경
                        if (success) {
                            isLaborActive = false
                            laborCount++
                            lastLaborEndTime = currentTimeString
                            previousLaborEndTime = currentTimeMs
                            laborDuration = durationToSend
                            laborStartTime = null
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

                        if (laborDuration.isNotEmpty()) {
                            Text(
                                text = "지속시간: $laborDuration",
                                style = MaterialTheme.typography.caption2,
                                color = MaterialTheme.colors.onSurface
                            )
                        }

                        if (laborInterval.isNotEmpty()) {
                            Text(
                                text = "간격: $laborInterval",
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
}