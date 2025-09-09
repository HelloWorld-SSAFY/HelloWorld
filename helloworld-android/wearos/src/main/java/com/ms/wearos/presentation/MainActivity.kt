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
import com.ms.wearos.utils.TestLoggingUtils

class MainActivity : ComponentActivity() {

    private val healthServicesClient by lazy { HealthServices.getClient(this) }
    private val measureClient by lazy { healthServicesClient.measureClient }

    // 콜백 인스턴스를 멤버 변수로 관리
    private var heartRateCallback: MeasureCallback? = null
    private var activityCallback: MeasureCallback? = null

    private var totalSteps = 0
    private var totalCalories = 0.0
    private var totalDistance = 0.0

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
            HealthDataApp()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 액티비티 종료 시 모든 콜백 해제
        lifecycleScope.launch {
            cleanupCallbacks()
        }
    }

    private suspend fun cleanupCallbacks() {
        try {
            heartRateCallback?.let { callback ->
                measureClient.unregisterMeasureCallback(DataType.HEART_RATE_BPM, callback)
                Log.d("MainActivity", "Heart rate callback unregistered in cleanup")
            }

            activityCallback?.let { callback ->
                measureClient.unregisterMeasureCallback(DataType.STEPS, callback)
                measureClient.unregisterMeasureCallback(DataType.CALORIES, callback)
                measureClient.unregisterMeasureCallback(DataType.DISTANCE, callback)
                Log.d("MainActivity", "Activity callbacks unregistered in cleanup")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error during cleanup", e)
        } finally {
            heartRateCallback = null
            activityCallback = null
        }
    }

    private fun requestHealthPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACTIVITY_RECOGNITION
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    @Composable
    fun HealthDataApp() {
        HelloWorldTheme {
            val scrollState = rememberScrollState()
            var heartRate by remember { mutableStateOf(0.0) }
            var stepCount by remember { mutableStateOf(0) }
            var calories by remember { mutableStateOf(0.0) }
            var distance by remember { mutableStateOf(0.0) }
            var isHeartRateMeasuring by remember { mutableStateOf(false) }
            var isStepsMeasuring by remember { mutableStateOf(false) }

            var heartStatus by remember { mutableStateOf("심박수: 준비됨") }
            var activityStatus by remember { mutableStateOf("활동량: 준비됨") }

            val scope = rememberCoroutineScope()

            // 심박수 콜백 생성 함수
            fun createHeartRateCallback() = object : MeasureCallback {
                override fun onAvailabilityChanged(
                    dataType: DeltaDataType<*, *>,
                    availability: Availability
                ) {
                    Log.d("HeartRate", "Availability: $availability")
                    heartStatus  = when (availability.toString()) {
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

            // 활동량 콜백 생성 함수
            fun createActivityCallback() = object : MeasureCallback {
                override fun onAvailabilityChanged(
                    dataType: DeltaDataType<*, *>,
                    availability: Availability
                ) {
                    Log.d("Activity", "Data type: $dataType, Availability: $availability")

                    activityStatus = when (availability.toString()) {
                        "AVAILABLE" -> "활동량: 측정 가능"
                        "UNAVAILABLE" -> "활동량: 측정 불가능"
                        "ACQUIRING" -> "활동량: 센서 준비 중"
                        "UNAVAILABLE_DEVICE_OFF_BODY" -> "활동량: 기기가 손목에서 분리됨"
                        "UNAVAILABLE_HIGH_POWER_DISABLED" -> "활동량: 고전력 모드 비활성화"
                        else -> "활동량: 상태 $availability"
                    }
                }

                override fun onDataReceived(data: DataPointContainer) {
                    if (isStepsMeasuring) {
                        // Delta steps (증분 걸음수)
                        val stepsData = data.getData(DataType.STEPS)
                        stepsData.forEach { dataPoint ->
                            val deltaSteps = dataPoint.value.toInt() // 증분 걸음수
                            totalSteps += deltaSteps // 총 걸음수에 추가
                            stepCount = totalSteps
                            Log.d("Activity", "새 걸음수: $deltaSteps, 총 걸음수: $totalSteps")
                        }

                        // Delta calories (증분 칼로리)
                        val caloriesData = data.getData(DataType.CALORIES)
                        caloriesData.forEach { dataPoint ->
                            val deltaCalories = dataPoint.value
                            totalCalories += deltaCalories
                            calories = totalCalories
                            Log.d("Activity", "새 칼로리: $deltaCalories, 총 칼로리: $totalCalories")
                        }

                        // Delta distance (증분 거리)
                        val distanceData = data.getData(DataType.DISTANCE)
                        distanceData.forEach { dataPoint ->
                            val deltaDistance = dataPoint.value
                            totalDistance += deltaDistance
                            distance = totalDistance
                            Log.d("Activity", "새 거리: $deltaDistance, 총 거리: $totalDistance")
                        }

                        // TestLoggingUtils로 실시간 활동량 로그 출력
                        TestLoggingUtils.logActivityData(stepCount, calories, distance)
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
                    text = "건강 데이터 모니터링",
                    style = MaterialTheme.typography.title3,
                    color = MaterialTheme.colors.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = heartStatus ,
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = activityStatus,
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 심박수 섹션
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {}
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "심박수",
                            style = MaterialTheme.typography.caption1,
                            color = MaterialTheme.colors.onSurface
                        )
                        Text(
                            text = "${heartRate.toInt()} BPM",
                            style = MaterialTheme.typography.title2,
                            color = if (isHeartRateMeasuring) Color.Red else MaterialTheme.colors.primary
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                            TestLoggingUtils.logMeasurementStart("심박수")
                                            Log.d("HeartRate", "Started heart rate measurement")
                                        } catch (e: Exception) {
                                            Log.e("HeartRate", "Error starting measurement", e)
                                            heartStatus  = "심박수 측정 시작 오류"
                                            isHeartRateMeasuring = false
                                        }
                                    }
                                },
                                enabled = !isHeartRateMeasuring,
                                modifier = Modifier.size(width = 60.dp, height = 32.dp)
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
                                            heartStatus  = "심박수 측정 중지됨"

                                            // TestLoggingUtils로 측정 중지 로그 출력
                                            TestLoggingUtils.logMeasurementStop("심박수")
                                            Log.d("HeartRate", "Stopped heart rate measurement completely")
                                        } catch (e: Exception) {
                                            Log.e("HeartRate", "Error stopping measurement", e)
                                            isHeartRateMeasuring = false
                                            heartRate = 0.0
                                            heartStatus  = "심박수 측정 중지 오류"
                                        }
                                    }
                                },
                                enabled = isHeartRateMeasuring,
                                modifier = Modifier.size(width = 60.dp, height = 32.dp)
                            ) {
                                Text("중지", style = MaterialTheme.typography.caption2)
                            }
                        }
                    }
                }

                // 활동량 섹션
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {}
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "활동량",
                            style = MaterialTheme.typography.caption1,
                            color = MaterialTheme.colors.onSurface
                        )

                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$stepCount",
                                    style = MaterialTheme.typography.title3,
                                    color = if (isStepsMeasuring) Color.Green else MaterialTheme.colors.primary
                                )
                                Text("걸음", style = MaterialTheme.typography.caption2)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${calories.toInt()}",
                                    style = MaterialTheme.typography.title3,
                                    color = if (isStepsMeasuring) Color.Green else MaterialTheme.colors.primary
                                )
                                Text("kcal", style = MaterialTheme.typography.caption2)
                            }
                        }

                        Text(
                            text = "${(distance / 1000).format(2)} km",
                            style = MaterialTheme.typography.body2,
                            color = if (isStepsMeasuring) Color.Green else MaterialTheme.colors.primary
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            // 이전 콜백이 있다면 먼저 해제
                                            activityCallback?.let { oldCallback ->
                                                measureClient.unregisterMeasureCallback(DataType.STEPS, oldCallback)
                                                measureClient.unregisterMeasureCallback(DataType.CALORIES, oldCallback)
                                                measureClient.unregisterMeasureCallback(DataType.DISTANCE, oldCallback)
                                            }

                                            // 새 콜백 생성 및 등록
                                            activityCallback = createActivityCallback()
                                            activityCallback?.let { callback ->
                                                measureClient.registerMeasureCallback(DataType.STEPS, callback)
                                                measureClient.registerMeasureCallback(DataType.CALORIES, callback)
                                                measureClient.registerMeasureCallback(DataType.DISTANCE, callback)
                                            }

                                            isStepsMeasuring = true
                                            activityStatus = "활동량 측정 시작됨"
                                            // TestLoggingUtils로 측정 시작 로그 출력
                                            TestLoggingUtils.logMeasurementStart("활동량")
                                            Log.d("Activity", "Started activity measurement")
                                        } catch (e: Exception) {
                                            Log.e("Activity", "Error starting measurement", e)
                                            isStepsMeasuring = false
                                            activityStatus = "활동량 측정 시작 오류"
                                        }
                                    }
                                },
                                enabled = !isStepsMeasuring,
                                modifier = Modifier.size(width = 60.dp, height = 32.dp)
                            ) {
                                Text("시작", style = MaterialTheme.typography.caption2)
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            // 먼저 측정 상태를 false로 변경
                                            isStepsMeasuring = false

                                            // 콜백 해제
                                            activityCallback?.let { callback ->
                                                measureClient.unregisterMeasureCallback(DataType.STEPS, callback)
                                                measureClient.unregisterMeasureCallback(DataType.CALORIES, callback)
                                                measureClient.unregisterMeasureCallback(DataType.DISTANCE, callback)
                                                Log.d("Activity", "Activity callbacks unregistered")
                                            }

                                            // 콜백 참조 제거
                                            activityCallback = null
                                            activityStatus = "활동량 측정 중지됨"

                                            // TestLoggingUtils로 측정 중지 로그 출력
                                            TestLoggingUtils.logMeasurementStop("활동량")
                                            Log.d("Activity", "Stopped activity measurement completely")
                                        } catch (e: Exception) {
                                            Log.e("Activity", "Error stopping measurement", e)
                                            isStepsMeasuring = false
                                        }
                                    }
                                },
                                enabled = isStepsMeasuring,
                                modifier = Modifier.size(width = 60.dp, height = 32.dp)
                            ) {
                                Text("중지", style = MaterialTheme.typography.caption2)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Double 확장 함수
fun Double.format(digits: Int) = "%.${digits}f".format(this)