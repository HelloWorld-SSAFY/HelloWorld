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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.health.connect.client.PermissionController
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
import com.ms.helloworld.util.LocationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.Manifest
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

    private val permissionLauncher =
        registerForActivityResult<Set<String>, Set<String>>(
            PermissionController.createRequestPermissionResultContract()
        ) { granted: Set<String> ->
            Log.d(TAG, "권한 요청 결과: $granted")
            if (granted.containsAll(healthConnectManager.permissions)) {
                Log.d(TAG, "모든 권한이 승인되었습니다. WorkManager 스케줄을 시작합니다.")
                stepsWorkScheduler.scheduleStepsUpload() // 이 라인 추가 필요
            } else {
                Log.e(TAG, "권한 요청 실패")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_HelloWorld)

        super.onCreate(savedInstanceState)

        // 위치 권한도 체크 필요
        checkLocationPermission()

        // 시스템 스플래시 제거
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { false }

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

        enableEdgeToEdge()

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

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ),
                1001
            )
        }
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
            "REMINDER" -> {
                navController.currentBackStackEntry?.savedStateHandle?.set("coupleId", coupleId)
                navController.navigate(Screen.CalendarScreen.route) {
                    launchSingleTop = true
                }
            }
            "EMERGENCY" -> {
                navController.currentBackStackEntry?.savedStateHandle?.set("coupleId", coupleId)
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
                    Log.d(TAG, "모든 권한이 승인되었습니다. WorkManager 스케줄을 시작합니다.")
                    stepsWorkScheduler.scheduleStepsUpload() // WorkManager 시작
                } else {
                    Log.d(TAG, "권한 요청을 시작합니다.")
                    permissionLauncher.launch(healthConnectManager.permissions)
                }
            } catch (e: Exception) {
                Log.e(TAG, "권한 확인 중 오류 발생: ${e.message}", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "앱이 포그라운드로 전환됨")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "앱이 백그라운드로 전환됨 - 헬스 데이터 수집 중단")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "앱 종료 - 헬스 데이터 수집 중단")
    }
}