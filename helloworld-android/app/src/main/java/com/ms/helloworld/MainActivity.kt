package com.ms.helloworld

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.ms.helloworld.navigation.MainNavigation
import com.ms.helloworld.navigation.Screen
import com.ms.helloworld.notification.NotificationChannels
import com.ms.helloworld.repository.FcmRepository
import com.ms.helloworld.ui.theme.HelloWorldTheme
import com.ms.helloworld.util.HealthConnectManager
import com.ms.helloworld.util.LocationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.HealthConnectClient
import com.ms.helloworld.util.StepsWorkScheduler

private const val TAG = "싸피_MainActivity"
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var healthConnectManager: HealthConnectManager

    @Inject
    lateinit var fcmRepository: FcmRepository

    @Inject
    lateinit var stepsWorkScheduler: StepsWorkScheduler

    @Inject
    lateinit var locationManager: LocationManager

    companion object {
        val deepLinkIntents = kotlinx.coroutines.flow.MutableSharedFlow<Intent>(
            extraBufferCapacity = 1
        )
    }

    // 안드로이드 기본 권한 결과 처리 추가
    private val androidPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
//        Log.d(TAG, "안드로이드 권한 결과: $permissions")

        val activityRecognitionGranted = permissions[Manifest.permission.ACTIVITY_RECOGNITION] ?: false
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val notificationGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else {
            true // API 33 미만에서는 알림 권한 불필요
        }

//        Log.d(TAG, "권한 상태 - 활동인식: $activityRecognitionGranted, 위치: $locationGranted, 알림: $notificationGranted")

        if (activityRecognitionGranted && locationGranted) {
//            Log.d(TAG, "기본 권한 승인됨. Health Connect 권한 확인을 진행합니다.")

            // 알림 권한이 있으면 FCM 토큰 등록
            if (notificationGranted) {
//                Log.d(TAG, "알림 권한도 승인됨. FCM 토큰을 등록합니다.")
//                fcmRepository.registerTokenAsync(platform = "ANDROID")
            }

            checkAndRequestHealthConnectPermissions()
        } else {
            Log.w(TAG, "기본 권한이 거부되었습니다.")
            if (!activityRecognitionGranted) {
                Log.w(TAG, "ACTIVITY_RECOGNITION 권한 없음 - 걸음수 데이터 수집 불가")
            }
            if (!locationGranted) {
                Log.w(TAG, "위치 권한 없음 - 위치 데이터 수집 불가")
            }
            if (!notificationGranted) {
                Log.w(TAG, "알림 권한 없음 - 푸시 알림 수신 불가")
            }

            // 서버 전송에 필요한 권한이 부족하지만, Health Connect는 여전히 확인
            checkAndRequestHealthConnectPermissions()
        }
    }

    private val healthConnectPermissionLauncher =
        registerForActivityResult<Set<String>, Set<String>>(
            PermissionController.createRequestPermissionResultContract()
        ) { granted: Set<String> ->
//            Log.d(TAG, "Health Connect 권한 요청 결과: $granted")
            if (granted.containsAll(healthConnectManager.permissions)) {
//                Log.d(TAG, "모든 Health Connect 권한이 승인되었습니다.")

            } else {
                Log.e(TAG, "Health Connect 권한 요청 실패: ${healthConnectManager.permissions - granted}")
            }

            // 결과와 상관없이 최종 권한 확인
            checkFinalPermissionsAndStartWork()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_HelloWorld)

        super.onCreate(savedInstanceState)

        // 시스템 스플래시 제거
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { false }

        // 권한 확인을 단계별로 진행
        checkAndRequestAndroidPermissions()

        // 알림 채널 생성
        NotificationChannels.createDefaultChannel(this)

        // 최초 Intent 처리 → 스트림으로 방출
        intent?.let { emitDeepLinkIntent(it) }

        // FCM 토큰 확인 (디버그용)
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener {
            Log.i("FCM", "현재 토큰: $it")
        }

        enableEdgeToEdge()

        setContent {

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

                    LaunchedEffect(navController) {
                        MainActivity.deepLinkIntents.collect { newIntent ->
                            handleInitialDeepLink(newIntent, navController)
                        }
                    }

                    MainNavigation(navController = navController)
                }
            }
        }
    }

    private fun checkAndRequestAndroidPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // ACTIVITY_RECOGNITION 권한 확인 (걸음수에 중요)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        // 위치 권한 확인
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // 알림 권한 확인 (API 33 이상)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
//            Log.d(TAG, "안드로이드 기본 권한 요청: $permissionsToRequest")
            androidPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
//            Log.d(TAG, "안드로이드 기본 권한이 모두 승인되어 있습니다.")
            // 알림 권한이 이미 있다면 FCM 토큰 등록
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
//                fcmRepository.registerTokenAsync(platform = "ANDROID")
            }
            checkAndRequestHealthConnectPermissions()
        }
    }

    private fun checkAndRequestHealthConnectPermissions() {
        lifecycleScope.launch {
            try {
                // Health Connect 사용 가능 여부 확인
                val sdkStatus = HealthConnectClient.getSdkStatus(this@MainActivity)
//                Log.d(TAG, "Health Connect SDK 상태: $sdkStatus")

                when (sdkStatus) {
                    HealthConnectClient.SDK_UNAVAILABLE -> {
                        Log.e(TAG, "Health Connect를 사용할 수 없습니다.")
                        return@launch
                    }
                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                        Log.e(TAG, "Health Connect 공급자 업데이트가 필요합니다.")
                        return@launch
                    }
                    else -> {
                        Log.d(TAG, "Health Connect 사용 가능")
                    }
                }

                val permissionStatus = healthConnectManager.checkPermissions()
//                Log.d(TAG, "현재 Health Connect 권한 상태: $permissionStatus")

                val allPermissionsGranted = permissionStatus.values.all { it }

                if (allPermissionsGranted) {
//                    Log.d(TAG, "모든 Health Connect 권한이 승인되었습니다.")
                    checkFinalPermissionsAndStartWork()
                } else {
                    Log.d(TAG, "Health Connect 권한 요청을 시작합니다.")
                    healthConnectPermissionLauncher.launch(healthConnectManager.permissions)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Health Connect 권한 확인 중 오류 발생: ${e.message}", e)
            }
        }
    }

    // 모든 권한 상태를 최종 확인하고 WorkManager 시작 여부 결정
    private fun checkFinalPermissionsAndStartWork() {
        val hasActivityRecognition = ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasNotification = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        lifecycleScope.launch {
            val healthConnectPermissions = healthConnectManager.checkPermissions()

            // 수정된 걸음수 권한 확인 로직
            val hasStepsPermission = healthConnectPermissions.entries.any { (permission, granted) ->
                val isStepsPermission = permission.contains("StepsRecord") || permission.contains("READ_STEPS")
//                Log.d(TAG, "권한 체크: $permission, 걸음수관련: $isStepsPermission, 승인: $granted")
                isStepsPermission && granted
            }

            Log.d(TAG, "=== 최종 권한 상태 확인 ===")
            Log.d(TAG, "ACTIVITY_RECOGNITION: $hasActivityRecognition")
            Log.d(TAG, "위치 권한: $hasLocation")
            Log.d(TAG, "알림 권한: $hasNotification")
            Log.d(TAG, "걸음수 권한: $hasStepsPermission")

            // 서버 전송에 필요한 모든 권한이 있는지 확인
            if (hasActivityRecognition && hasLocation && hasStepsPermission) {
                Log.d(TAG, "모든 필수 권한 승인됨. WorkManager를 시작합니다.")
                stepsWorkScheduler.scheduleStepsUpload()

                if (!hasNotification) {
                    Log.w(TAG, "알림 권한 없음 - 푸시 알림을 받을 수 없습니다.")
                }
            } else {
                Log.w(TAG, "서버 전송에 필요한 권한이 부족합니다.")
                Log.w(TAG, "   - 걸음수 + 위치 데이터 전송 불가")

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
        val hasInfo = intent.hasExtra("deeplink_type")
        if (hasInfo) {
            // 최신 값 push (버퍼가 있으면 drop 되지 않음)
            MainActivity.deepLinkIntents.tryEmit(intent)
        }
    }

    // 앱이 완전히 종료된 상태에서 알림을 눌렀을 때의 처리
    private fun handleInitialDeepLink(intent: Intent, navController: NavHostController) {
        val deeplinkType = intent.getStringExtra("deeplink_type")

        Log.d("DeepLink", "초기 딥링크 처리 - type=$deeplinkType")

        // 타입별 화면으로 이동
        when (deeplinkType) {
            "REMINDER" -> {
                navController.navigate(Screen.CalendarScreen.route) {
                    launchSingleTop = true
                }
            }
            "EMERGENCY" -> {
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
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}