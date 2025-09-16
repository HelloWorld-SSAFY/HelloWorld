package com.ms.wearos.presentation

import android.Manifest
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.*
import androidx.health.services.client.unregisterMeasureCallback
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.*
import kotlinx.coroutines.launch
import com.ms.wearos.presentation.theme.HelloWorldTheme
import com.ms.wearos.util.TestLoggingUtils

class MainActivity : ComponentActivity() {

    private val healthServicesClient by lazy { HealthServices.getClient(this) }
    private val measureClient by lazy { healthServicesClient.measureClient }

    // 심박수 콜백만 관리
    private var heartRateCallback: MeasureCallback? = null

    // 권한 요청 런처
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        Log.d("Permissions", "All permissions granted: $allGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        requestHealthPermissions()

        setContent {
            HeartRateApp()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 액티비티 종료 시 심박수 콜백 해제
        lifecycleScope.launch {
            cleanupCallback()
        }
    }

    private suspend fun cleanupCallback() {
        try {
            heartRateCallback?.let { callback ->
                measureClient.unregisterMeasureCallback(DataType.HEART_RATE_BPM, callback)
                Log.d("MainActivity", "Heart rate callback unregistered in cleanup")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error during cleanup", e)
        } finally {
            heartRateCallback = null
        }
    }

    private fun requestHealthPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BODY_SENSORS
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    @Composable
    fun HeartRateApp() {
        HelloWorldTheme {
            val scrollState = rememberScrollState()
            var heartRate by remember { mutableStateOf(0.0) }
            var isHeartRateMeasuring by remember { mutableStateOf(false) }
            var heartStatus by remember { mutableStateOf("심박수: 준비됨") }

            val scope = rememberCoroutineScope()

            // 심박수 콜백 생성 함수
            fun createHeartRateCallback() = object : MeasureCallback {
                override fun onAvailabilityChanged(
                    dataType: DeltaDataType<*, *>,
                    availability: Availability
                ) {
                    Log.d("HeartRate", "Availability: $availability")
                    heartStatus = when (availability.toString()) {
                        "AVAILABLE" -> "심박수 측정 가능"
                        "UNAVAILABLE" -> "심박수 측정 불가능"
                        "ACQUIRING" -> "심박수 센서 준비중"
                        "UNAVAILABLE_DEVICE_OFF_BODY" -> "기기가 손목에서 분리됨"
                        "UNAVAILABLE_HIGH_POWER_DISABLED" -> "고전력 모드 비활성화"
                        else -> "상태: $availability"
                    }
                }

                override fun onDataReceived(data: DataPointContainer) {
                    if (isHeartRateMeasuring) {
                        val heartRateData = data.getData(DataType.HEART_RATE_BPM)
                        heartRateData.forEach { dataPoint ->
                            val bpm = dataPoint.value
                            heartRate = bpm
                            // TestLoggingUtils로 실시간 심박수 로그 출력
                            TestLoggingUtils.logHeartRateData(bpm)
                        }
                    }
                }
            }

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
                    text = "심박수 모니터링",
                    style = MaterialTheme.typography.title3,
                    color = MaterialTheme.colors.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = heartStatus,
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 심박수 섹션
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {}
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "심박수",
                            style = MaterialTheme.typography.caption1,
                            color = MaterialTheme.colors.onSurface
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "${heartRate.toInt()} BPM",
                            style = MaterialTheme.typography.title1,
                            color = if (isHeartRateMeasuring) Color.Red else MaterialTheme.colors.primary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            // 이전 콜백이 있다면 먼저 해제
                                            heartRateCallback?.let { oldCallback ->
                                                measureClient.unregisterMeasureCallback(
                                                    DataType.HEART_RATE_BPM,
                                                    oldCallback
                                                )
                                            }

                                            // 새 콜백 생성 및 등록
                                            heartRateCallback = createHeartRateCallback()
                                            heartRateCallback?.let { callback ->
                                                measureClient.registerMeasureCallback(
                                                    DataType.HEART_RATE_BPM,
                                                    callback
                                                )
                                            }

                                            isHeartRateMeasuring = true
                                            // TestLoggingUtils로 측정 시작 로그 출력
                                            TestLoggingUtils.logMeasurementStart()
                                            Log.d("HeartRate", "Started heart rate measurement")
                                        } catch (e: Exception) {
                                            Log.e("HeartRate", "Error starting measurement", e)
                                            heartStatus = "심박수 측정 시작 오류"
                                            isHeartRateMeasuring = false
                                        }
                                    }
                                },
                                enabled = !isHeartRateMeasuring,
                                modifier = Modifier.size(width = 70.dp, height = 36.dp)
                            ) {
                                Text("시작", style = MaterialTheme.typography.caption2)
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            // 먼저 측정 상태를 false로 변경
                                            isHeartRateMeasuring = false

                                            // 콜백 해제
                                            heartRateCallback?.let { callback ->
                                                measureClient.unregisterMeasureCallback(
                                                    DataType.HEART_RATE_BPM,
                                                    callback
                                                )
                                                Log.d("HeartRate", "Heart rate callback unregistered")
                                            }

                                            // 콜백 참조 제거
                                            heartRateCallback = null

                                            // UI 값 초기화
                                            heartRate = 0.0
                                            heartStatus = "심박수 측정 중지됨"

                                            // TestLoggingUtils로 측정 중지 로그 출력
                                            TestLoggingUtils.logMeasurementStop("심박수")
                                            Log.d("HeartRate", "Stopped heart rate measurement completely")
                                        } catch (e: Exception) {
                                            Log.e("HeartRate", "Error stopping measurement", e)
                                            isHeartRateMeasuring = false
                                            heartRate = 0.0
                                            heartStatus = "심박수 측정 중지 오류"
                                        }
                                    }
                                },
                                enabled = isHeartRateMeasuring,
                                modifier = Modifier.size(width = 70.dp, height = 36.dp)
                            ) {
                                Text("중지", style = MaterialTheme.typography.caption2)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 심박수 상태 정보
                if (isHeartRateMeasuring) {
                    Text(
                        text = "실시간 심박수를 측정하고 있습니다",
                        style = MaterialTheme.typography.caption2,
                        color = Color.Green,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}