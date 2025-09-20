package com.ms.helloworld

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.ms.helloworld.navigation.MainNavigation
import com.ms.helloworld.navigation.Screen
import com.ms.helloworld.notification.NotificationChannels
import com.ms.helloworld.notification.NotificationPermissionRequester
import com.ms.helloworld.repository.FcmRepository
import com.ms.helloworld.ui.theme.HelloWorldTheme
import com.ms.helloworld.util.HealthConnectManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject

private const val TAG = "싸피_MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var healthConnectManager: HealthConnectManager
    private var isDataFetchingActive = false

    @Inject
    lateinit var fcmRepository: FcmRepository

    companion object {
        val deepLinkIntents = kotlinx.coroutines.flow.MutableSharedFlow<Intent>(
            extraBufferCapacity = 1
        )
    }

    private val permissionLauncher =
        registerForActivityResult<Set<String>, Set<String>>(
            PermissionController.createRequestPermissionResultContract()
        ) { granted: Set<String> ->
            Log.d(TAG, "권한 요청 결과: $granted")
            if (granted.containsAll(healthConnectManager.permissions)) {
                Log.d(TAG, "모든 권한이 승인되었습니다. 데이터 수집을 시작합니다.")
                startPeriodicHealthDataFetching()
            } else {
                Log.e(TAG, "권한 요청 실패")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        healthConnectManager = HealthConnectManager(this)

        // 알림 채널 생성
        NotificationChannels.createDefaultChannel(this)

        // 최초 Intent 처리 → 스트림으로 방출
        intent?.let { emitDeepLinkIntent(it) }

        // FCM 토큰 확인 (디버그용)
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener {
            Log.i("FCM", "현재 토큰: $it")
        }

        // 앱 시작시 권한 확인 및 요청
        checkAndRequestPermissions()

        setContent {
            // 알림 권한 요청
            NotificationPermissionRequester(
                autoRequest = true,
                onGranted = {
                    fcmRepository.registerTokenAsync(platform = "ANDROID")
                }
            )

            HelloWorldTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars)
                ) {
                    val navController = rememberNavController()

                    // 앱 시작 시 딥링크 처리
                    LaunchedEffect(Unit) {
                        intent?.let { handleInitialDeepLink(it, navController) }
                    }

                    MainNavigation(navController = navController)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        emitDeepLinkIntent(intent) // 포그라운드 클릭 시 여기로 옴
    }

    private fun emitDeepLinkIntent(intent: Intent) {
        Log.d("DeepLink", "emit intent: action=${intent.action}, extras=${intent.extras}")
        // 실제 딥링크 정보가 없으면 패스
        val hasInfo = intent.hasExtra("deeplink_type") && intent.hasExtra("coupleId")
        if (hasInfo) {
            // 최신 값 push (버퍼가 있으면 drop 되지 않음)
            MainActivity.deepLinkIntents.tryEmit(intent)
        }
    }

    // 앱이 완전히 종료된 상태에서 알림을 눌렀을 때의 처리
    private fun handleInitialDeepLink(intent: Intent, navController: NavHostController) {
        val deeplinkType = intent.getStringExtra("deeplink_type")
        val coupleId = intent.getStringExtra("coupleId")

        Log.d("DeepLink", "초기 딥링크 처리 - type=$deeplinkType, coupleId=$coupleId")

        if (coupleId.isNullOrEmpty()) return

        // 타입별 화면으로 이동
        when (deeplinkType) {
            "calendar" -> {
                navController.currentBackStackEntry?.savedStateHandle?.set("coupleId", coupleId)
                navController.navigate(Screen.CalendarScreen.route) {
                    launchSingleTop = true
                }
            }
            "health_heart_rate" -> {
                navController.currentBackStackEntry?.savedStateHandle?.set("coupleId", coupleId)
                navController.currentBackStackEntry?.savedStateHandle?.set("healthType", "heart_rate")
                navController.navigate(Screen.WearableRecommendedScreen.route) {
                    launchSingleTop = true
                }
            }
            "health_stress" -> {
                navController.currentBackStackEntry?.savedStateHandle?.set("coupleId", coupleId)
                navController.currentBackStackEntry?.savedStateHandle?.set("healthType", "stress")
                navController.navigate(Screen.WearableRecommendedScreen.route) {
                    launchSingleTop = true
                }
            }
            "health_activity" -> {
                navController.currentBackStackEntry?.savedStateHandle?.set("coupleId", coupleId)
                navController.currentBackStackEntry?.savedStateHandle?.set("healthType", "activity")
                navController.navigate(Screen.WearableRecommendedScreen.route) {
                    launchSingleTop = true
                }
            }
            "main" -> {
                navController.navigate(Screen.HomeScreen.route) {
                    launchSingleTop = true
                }
            }
        }

        // 처리 후 Intent 정리
        intent.removeExtra("deeplink_type")
        intent.removeExtra("coupleId")
    }

    private fun checkAndRequestPermissions() {
        lifecycleScope.launch {
            try {
                val permissionStatus = healthConnectManager.checkPermissions()
                Log.d(TAG, "현재 권한 상태: $permissionStatus")

                val allPermissionsGranted = permissionStatus.values.all { it }

                if (allPermissionsGranted) {
                    Log.d(TAG, "모든 권한이 이미 승인되어 있습니다. 10초 간격 데이터 수집을 시작합니다.")
                    startPeriodicHealthDataFetching()
                } else {
                    Log.d(TAG, "권한 요청을 시작합니다.")
                    permissionLauncher.launch(healthConnectManager.permissions)
                }
            } catch (e: Exception) {
                Log.e(TAG, "권한 확인 중 오류 발생: ${e.message}", e)
            }
        }
    }

    private fun startPeriodicHealthDataFetching() {
        if (isDataFetchingActive) {
            Log.d(TAG, "이미 데이터 수집이 활성화되어 있습니다.")
            return
        }

        isDataFetchingActive = true
        Log.d(TAG, "10초 간격으로 헬스 데이터 수집을 시작합니다.")

        lifecycleScope.launch {
            var fetchCount = 1

            while (isDataFetchingActive) {
                try {
                    Log.d(TAG, "=== 데이터 수집 ${fetchCount}회차 시작 ===")
                    fetchHealthData()
                    Log.d(TAG, "=== 데이터 수집 ${fetchCount}회차 완료 ===")

                    fetchCount++
                    delay(10000) // 10초 대기

                } catch (e: Exception) {
                    Log.e(TAG, "데이터 수집 중 오류 발생 (${fetchCount}회차): ${e.message}", e)
                    delay(10000) // 오류 발생시에도 10초 후 재시도
                }
            }
        }
    }

    private suspend fun fetchHealthData() {
        try {
            // 헬스커넥트 클라이언트 상태 확인
            val isAvailable = androidx.health.connect.client.HealthConnectClient.getSdkStatus(this@MainActivity)
            Log.d(TAG, "헬스커넥트 SDK 상태: $isAvailable")

            // 걸음수 데이터 읽기
            Log.d(TAG, "걸음수 데이터 읽기 시작...")
            val stepRecords = healthConnectManager.readStepCounts()
            val stepData = stepRecords.map { it.count.toInt() }
            val totalSteps = stepData.sum()

            // 칼로리 소모량 데이터 읽기
            Log.d(TAG, "칼로리 데이터 읽기 시작...")
            val caloriesBurnedRecords = healthConnectManager.readCaloriesBurned()
            val dailyCaloriesBurned = caloriesBurnedRecords.sumOf { it.energy.inKilocalories }

            // 심박수 데이터 읽기
            Log.d(TAG, "심박수 데이터 읽기 시작...")
            val heartRateRecords = healthConnectManager.readHeartRates()
            val avgHeartRate = if (heartRateRecords.isNotEmpty()) {
                val allBpm = heartRateRecords.flatMap { record ->
                    record.samples.map { it.beatsPerMinute }
                }
                if (allBpm.isNotEmpty()) allBpm.average() else 0.0
            } else 0.0

            // 오늘 걸은 거리
            val distanceRecords = healthConnectManager.readDistanceWalked()
            val totalDistance = distanceRecords.sumOf { it.distance.inMeters }

            // 활동으로 소모한 칼로리
            val activeCalorieRecords = healthConnectManager.readActiveCaloriesBurned()
            val activeCaloriesBurned = activeCalorieRecords.sumOf { it.energy.inKilocalories }

            // 수면 기록
            val sleepSessions = healthConnectManager.readSleepSessions()
            val totalSleepMillis = sleepSessions.sumOf { Duration.between(it.startTime, it.endTime).toMillis() }
            val totalSleepInMinutes = totalSleepMillis / 1000 / 60
            var remMinutes = 0L
            var deepMinutes = 0L
            var lightMinutes = 0L

            sleepSessions.forEach { session ->
                session.stages.forEach { stage ->
                    val duration = Duration.between(stage.startTime, stage.endTime).toMinutes()
                    when (stage.stage) {
                        SleepSessionRecord.STAGE_TYPE_REM -> remMinutes += duration
                        SleepSessionRecord.STAGE_TYPE_DEEP -> deepMinutes += duration
                        SleepSessionRecord.STAGE_TYPE_LIGHT -> lightMinutes += duration
                    }
                }
            }

            // 헬스 데이터 로그 출력
            Log.d(TAG, "=== 헬스 데이터 현황 ===")
            Log.d(TAG, "총 걸음수: ${totalSteps}보 (레코드 ${stepRecords.size}개)")
            Log.d(TAG, "평균 심박수: ${"%.1f".format(avgHeartRate)} BPM (레코드 ${heartRateRecords.size}개)")
            Log.d(TAG, "총 소모 칼로리: ${"%.2f".format(dailyCaloriesBurned)} kcal")
            Log.d(TAG, "걸은 거리: ${"%.2f".format(totalDistance)} m")
            Log.d(TAG, "활동 칼로리: ${"%.2f".format(activeCaloriesBurned)} kcal")
            Log.d(TAG, "총 수면: ${totalSleepInMinutes}분 (깊은수면: ${deepMinutes}분, REM: ${remMinutes}분, 얕은수면: ${lightMinutes}분)")

            // 시간 정보도 출력
            Log.d(TAG, "데이터 수집 시간: ${java.time.LocalDateTime.now()}")

        } catch (e: Exception) {
            Log.e(TAG, "헬스 데이터 수집 중 오류: ${e.message}", e)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "앱이 포그라운드로 전환됨")
        // 앱이 다시 포그라운드로 올 때 데이터 수집이 중단되어 있다면 재시작
        if (!isDataFetchingActive) {
            checkAndRequestPermissions()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "앱이 백그라운드로 전환됨 - 헬스 데이터 수집 중단")
        isDataFetchingActive = false
    }

    override fun onDestroy() {
        super.onDestroy()
        isDataFetchingActive = false
        Log.d(TAG, "앱 종료 - 헬스 데이터 수집 중단")
    }
}

// HealthData 클래스를 정의
data class HealthData(
    val stepData: List<Int>,
    val heartRateData: List<HeartRateData>,
    val caloriesBurnedData: Double,
    val distanceWalked: Double,
    val activeCaloriesBurned: Double,
    val totalSleepMinutes: Long,
    val deepSleepMinutes: Long,
    val remSleepMinutes: Long,
    val lightSleepMinutes: Long
)

data class HeartRateData(val bpm: Double, val time: String)