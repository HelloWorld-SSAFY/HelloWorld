package com.ms.wearos.presentation

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
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
import kotlinx.coroutines.launch
import com.ms.wearos.presentation.theme.HelloWorldTheme
import com.ms.wearos.service.HealthDataService
import com.ms.wearos.service.HealthServiceHelper

private const val TAG = "싸피_MainActivity"
class MainActivity : ComponentActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    // 화면 상태 관리
    private var currentScreen = mutableStateOf(Screen.Main)

    // UI 상태 관리
    private var currentHeartRate = mutableStateOf(0.0)
    private var isToggleEnabled = mutableStateOf(false)
    private var heartStatus = mutableStateOf("심박수: 준비됨")
    private var lastUpdateTime = mutableStateOf("")

    // 화면 enum
    enum class Screen {
        Main, HeartRate, FetalMovement, LaborRecord
    }

    // 서비스로부터 심박수 데이터를 받기 위한 브로드캐스트 리시버
    private val heartRateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                HealthDataService.ACTION_HEART_RATE_UPDATE -> {
                    val heartRate = intent.getDoubleExtra("heart_rate", 0.0)
                    val timestamp = intent.getStringExtra("timestamp") ?: ""
                    val status = intent.getStringExtra("status") ?: ""

                    currentHeartRate.value = heartRate
                    lastUpdateTime.value = timestamp
                    if (status.isNotEmpty()) {
                        heartStatus.value = status
                    }

                    Log.d(TAG, "UI updated - Heart rate: $heartRate BPM")
                }

                HealthDataService.ACTION_SERVICE_STATUS -> {
                    val isRunning = intent.getBooleanExtra("is_running", false)
                    val status = intent.getStringExtra("status") ?: ""

                    isToggleEnabled.value = isRunning
                    if (status.isNotEmpty()) {
                        heartStatus.value = status
                    }

                    Log.d(TAG, "Service status updated: $isRunning")
                }
            }
        }
    }

    // 권한 요청 런처
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        Log.d("Permissions", "All permissions granted: $allGranted")
        if (allGranted) {
            checkAndRestoreToggleState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("heart_rate_prefs", Context.MODE_PRIVATE)

        // 저장된 토글 상태 복원
        isToggleEnabled.value = sharedPreferences.getBoolean("heart_rate_toggle", false)

        requestHealthPermissions()
        registerBroadcastReceiver()

        setContent {
            AppContent()
        }
    }

    override fun onResume() {
        super.onResume()
        // 화면 복귀 시 현재 서비스 상태 확인
        HealthServiceHelper.requestServiceStatus(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(heartRateReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
    }

    private fun registerBroadcastReceiver() {
        val filter = IntentFilter().apply {
            addAction(HealthDataService.ACTION_HEART_RATE_UPDATE)
            addAction(HealthDataService.ACTION_SERVICE_STATUS)
        }
        registerReceiver(heartRateReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    private fun checkAndRestoreToggleState() {
        if (isToggleEnabled.value) {
            Log.d(TAG, "Restoring toggle state - starting service")
            HealthServiceHelper.startService(this)
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

    // 토글 상태 변경
    private fun toggleHeartRateMeasurement(enabled: Boolean) {
        lifecycleScope.launch {
            // SharedPreferences에 상태 저장
            sharedPreferences.edit()
                .putBoolean("heart_rate_toggle", enabled)
                .apply()

            if (enabled) {
                Log.d(TAG, "Starting heart rate service")
                HealthServiceHelper.startService(this@MainActivity)
                heartStatus.value = "서비스 시작 중..."
            } else {
                Log.d(TAG, "Stopping heart rate service")
                HealthServiceHelper.stopService(this@MainActivity)
                currentHeartRate.value = 0.0
                lastUpdateTime.value = ""
                heartStatus.value = "측정 중지됨"
                isToggleEnabled.value = false
            }
        }
    }

    @Composable
    fun AppContent() {
        HelloWorldTheme {
            val screen by currentScreen

            when (screen) {
                Screen.Main -> MainScreen()
                Screen.HeartRate -> HeartRateScreen()
                Screen.FetalMovement -> FetalMovementScreen()
                Screen.LaborRecord -> LaborRecordScreen()
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


            // 심박수 카드
            MenuCard(
                title = "심박수",
                icon = Icons.Default.Favorite,
                description = "심박수 측정",
                onClick = { currentScreen.value = Screen.HeartRate }
            )

            // 태동 기록 카드
            MenuCard(
                title = "태동 기록",
                icon = Icons.Default.Face,
                description = "태동 기록하기",
                onClick = { currentScreen.value = Screen.FetalMovement }
            )

            // 진통 기록 카드
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
    fun HeartRateScreen() {
        val scrollState = rememberScrollState()

        // BackHandler 추가 - 뒤로가기 시 메인화면으로
        BackHandler {
            currentScreen.value = Screen.Main
        }

        // 클래스 레벨 상태를 Compose에서 관찰
        val heartRate by currentHeartRate
        val toggleEnabled by isToggleEnabled
        val status by heartStatus
        val updateTime by lastUpdateTime

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

            // 심박수 섹션
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

                    // 토글 버튼
                    ToggleButton(
                        checked = toggleEnabled,
                        onCheckedChange = { enabled ->
                            toggleHeartRateMeasurement(enabled)
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
    fun FetalMovementScreen() {
        // 태동 기록을 위한 상태 관리
        var fetalMovementCount by remember { mutableStateOf(0) }
        var lastRecordTime by remember { mutableStateOf("") }

        // BackHandler 추가 - 뒤로가기 시 메인화면으로
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

            // 제목
            Text(
                text = "태동 기록",
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center
            )

            // 아이콘
            Icon(
                imageVector = Icons.Default.Face,
                contentDescription = "태동",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colors.primary
            )

            // 태동 기록 버튼
            Button(
                onClick = {
                    fetalMovementCount++
                    val currentTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date())
                    lastRecordTime = currentTime

                    Log.d(TAG, "태동 기록됨: $fetalMovementCount 회, 시간: $currentTime")
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
    fun LaborRecordScreen() {
        // 진통 기록을 위한 상태 관리
        var isLaborActive by remember { mutableStateOf(false) }
        var laborStartTime by remember { mutableStateOf<Long?>(null) }
        var laborDuration by remember { mutableStateOf("") }
        var laborCount by remember { mutableStateOf(0) }
        var lastLaborEndTime by remember { mutableStateOf("") }
        var laborInterval by remember { mutableStateOf("") }
        var previousLaborEndTime by remember { mutableStateOf<Long?>(null) }

        // BackHandler 추가 - 뒤로가기 시 메인화면으로
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

            // 제목
            Text(
                text = "진통 기록",
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center
            )

            // 아이콘
            Icon(
                imageVector = Icons.Default.MonitorHeart,
                contentDescription = "진통",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colors.primary
            )

            // 진통 시작/종료 토글 버튼
            Button(
                onClick = {
                    val currentTimeMs = System.currentTimeMillis()
                    val currentTimeString = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date(currentTimeMs))

                    if (!isLaborActive) {
                        // 진통 시작
                        isLaborActive = true
                        laborStartTime = currentTimeMs
                        laborDuration = ""

                        // 이전 진통과의 간격 계산
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
                        isLaborActive = false
                        laborCount++

                        lastLaborEndTime = currentTimeString
                        previousLaborEndTime = currentTimeMs

                        // 진통 지속 시간 계산
                        if (laborStartTime != null) {
                            val durationMs = currentTimeMs - laborStartTime!!
                            val durationMinutes = durationMs / (1000 * 60)
                            val durationSeconds = (durationMs % (1000 * 60)) / 1000
                            laborDuration = "${durationMinutes}분 ${durationSeconds}초"

                            Log.d(TAG, "진통 종료됨: $currentTimeString, 지속시간: $laborDuration, 총 횟수: $laborCount")
                        }

                        laborStartTime = null
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

            // 진통 정보 표시 카드
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

                        // 현재 상태
                        Text(
                            text = if (isLaborActive) "진통 중..." else "진통 대기 중",
                            style = MaterialTheme.typography.body2,
                            color = if (isLaborActive) Color.Red else MaterialTheme.colors.onSurface
                        )

                        // 총 진통 횟수
                        if (laborCount > 0) {
                            Text(
                                text = "총 진통 횟수: ${laborCount}회",
                                style = MaterialTheme.typography.caption2,
                                color = MaterialTheme.colors.onSurface
                            )
                        }

                        // 마지막 진통 지속시간
                        if (laborDuration.isNotEmpty()) {
                            Text(
                                text = "지속시간: $laborDuration",
                                style = MaterialTheme.typography.caption2,
                                color = MaterialTheme.colors.onSurface
                            )
                        }

                        // 진통 간격
                        if (laborInterval.isNotEmpty()) {
                            Text(
                                text = "간격: $laborInterval",
                                style = MaterialTheme.typography.caption2,
                                color = MaterialTheme.colors.onSurface
                            )
                        }

                        // 마지막 진통 종료 시간
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