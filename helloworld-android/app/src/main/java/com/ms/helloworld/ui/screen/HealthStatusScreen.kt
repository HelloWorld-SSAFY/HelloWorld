package com.ms.helloworld.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ms.helloworld.dto.response.MaternalHealthGetResponse
import com.ms.helloworld.dto.response.MaternalHealthItem
import com.ms.helloworld.viewmodel.HealthViewModel
import com.ms.helloworld.viewmodel.HomeViewModel
import android.util.Log
import kotlin.math.max
import kotlin.math.abs
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// 건강 지표 타입
enum class HealthType(
    val displayName: String,
    val unit: String,
    val normalRange: Pair<Float, Float>
) {
    WEIGHT("체중", "kg", Pair(45f, 80f)),
    BLOOD_PRESSURE("혈압", "mmHg", Pair(90f, 140f)),
    BLOOD_SUGAR("혈당", "mg/dL", Pair(70f, 140f))
}

// 건강 데이터
data class HealthData(
    val day: Int,
    val weight: Float?,
    val bloodPressureHigh: Float?,
    val bloodPressureLow: Float?,
    val bloodSugar: Float?,
    val recordDate: String? = null // YYYY-MM-DD 형식
)

@SuppressLint("NewApi")
@Composable
fun HealthStatusScreen(
    navController: NavHostController
) {
    val backgroundColor = Color(0xFFF5F5F5)
    var selectedHealthType by remember { mutableStateOf(HealthType.WEIGHT) }
    var selectedDataPoint by remember { mutableStateOf<HealthData?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // HealthViewModel 추가
    val healthViewModel: HealthViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val healthState by healthViewModel.state.collectAsStateWithLifecycle()

    // HomeViewModel 추가 (DiaryDetailScreen과 동일)
    val homeViewModel: HomeViewModel = hiltViewModel()
    val momProfile by homeViewModel.momProfile.collectAsState()
    val menstrualDate by homeViewModel.menstrualDate.collectAsState()
    val currentPregnancyDay by homeViewModel.currentPregnancyDay.collectAsState()

    // 현재 주차의 시작일과 끝일 계산 (currentPregnancyDay 기준)
    val weekStartDay = if (currentPregnancyDay > 1) {
        val currentWeek = ((currentPregnancyDay - 1) / 7) + 1
        (currentWeek - 1) * 7 + 1
    } else 1
    val weekEndDay = weekStartDay + 6

    Log.d("HealthStatusScreen", "🔍 주차 계산: currentPregnancyDay=$currentPregnancyDay, weekStartDay=$weekStartDay, weekEndDay=$weekEndDay")

    // 애니메이션 상태
    var targetHealthType by remember { mutableStateOf(selectedHealthType) }
    val transition = updateTransition(targetState = targetHealthType, label = "healthTypeTransition")

    val slideOffset by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 300, easing = FastOutSlowInEasing) },
        label = "slideOffset"
    ) { healthType ->
        val currentIndex = HealthType.values().indexOf(selectedHealthType)
        val targetIndex = HealthType.values().indexOf(healthType)
        (currentIndex - targetIndex).toFloat() * 100f
    }

    // 서버 데이터를 HealthData 형식으로 변환 (주차별 1일~7일 순서)
    val healthDataList = remember(healthState.healthHistory, healthState.todayHealthData, menstrualDate, weekStartDay, weekEndDay) {
        if (menstrualDate == null) {
            Log.d("HealthStatusScreen", "menstrualDate가 null이므로 빈 데이터 반환")
            emptyList<HealthData>()
        } else {
            Log.d("HealthStatusScreen", "주차별 건강 데이터 정렬: ${weekStartDay}일~${weekEndDay}일")

            // 모든 서버 데이터를 날짜별로 정리 (MaternalHealthItem과 MaternalHealthGetResponse 혼용)
            val historyDataMap = mutableMapOf<String, MaternalHealthItem>()
            val todayDataMap = mutableMapOf<String, MaternalHealthGetResponse>()

            // 히스토리 데이터 추가 (MaternalHealthItem)
            healthState.healthHistory.forEach { item ->
                historyDataMap[item.recordDate] = item
            }

            // 오늘 데이터 추가 (MaternalHealthGetResponse)
            healthState.todayHealthData?.let { todayData ->
                Log.d("HealthStatusScreen", "🔍 todayHealthData 있음: ${todayData.recordDate}")
                todayDataMap[todayData.recordDate] = todayData
            } ?: run {
                Log.d("HealthStatusScreen", "🔍 todayHealthData 없음")
            }

            // 현재 주차의 1일~7일 순서대로 HealthData 생성
            val weeklyData = mutableListOf<HealthData>()
            val today = java.time.LocalDate.now().toString()
            Log.d("HealthStatusScreen", "🔍 오늘 날짜: $today, 현재 주차 범위: ${weekStartDay}~${weekEndDay}일")

            // 생성될 날짜 범위 미리 확인
            val dateRange = (weekStartDay..weekEndDay).map { day ->
                try {
                    val lmpDate = LocalDate.parse(menstrualDate)
                    day to lmpDate.plusDays(day.toLong()).toString()
                } catch (e: Exception) {
                    day to "error"
                }
            }
            Log.d("HealthStatusScreen", "🔍 생성될 날짜 범위: $dateRange")

            for (day in weekStartDay..weekEndDay) {
                // 임신 일수를 날짜로 변환
                val targetDate = try {
                    val lmpDate = LocalDate.parse(menstrualDate)
                    lmpDate.plusDays(day.toLong())
                } catch (e: Exception) {
                    Log.e("HealthStatusScreen", "날짜 계산 오류: ${e.message}")
                    null
                }

                val targetDateString = targetDate?.toString()
                Log.d("HealthStatusScreen", "${day}일차 -> 날짜: $targetDateString")

                if (targetDateString == today) {
                    Log.d("HealthStatusScreen", "🎯 오늘 날짜 발견: ${day}일차 = $targetDateString")
                }

                // 해당 날짜의 서버 데이터 찾기 (히스토리 우선, 없으면 오늘 데이터)
                val historyData = targetDateString?.let { historyDataMap[it] }
                val todayData = targetDateString?.let { todayDataMap[it] }

                if (targetDateString == today) {
                    Log.d("HealthStatusScreen", "🎯 오늘 데이터 검색 결과: historyData=$historyData, todayData=$todayData")
                }

                when {
                    historyData != null -> {
                        // 히스토리 데이터가 있는 경우 (MaternalHealthItem)
                        val bloodPressure = healthViewModel.parseBloodPressure(historyData.bloodPressure)
                        weeklyData.add(HealthData(
                            day = (day - weekStartDay) + 1, // 주차 내 1~7일
                            weight = historyData.weight.toFloat(),
                            bloodPressureHigh = bloodPressure?.first?.toFloat(),
                            bloodPressureLow = bloodPressure?.second?.toFloat(),
                            bloodSugar = historyData.bloodSugar.toFloat(),
                            recordDate = historyData.recordDate
                        ))
                        Log.d("HealthStatusScreen", "${day}일차 히스토리 데이터: 체중=${historyData.weight}")
                    }
                    todayData != null -> {
                        // 오늘 데이터가 있는 경우 (MaternalHealthGetResponse)
                        val bloodPressure = healthViewModel.parseBloodPressure(todayData.bloodPressure)
                        weeklyData.add(HealthData(
                            day = (day - weekStartDay) + 1, // 주차 내 1~7일
                            weight = todayData.weight.toFloat(),
                            bloodPressureHigh = bloodPressure?.first?.toFloat(),
                            bloodPressureLow = bloodPressure?.second?.toFloat(),
                            bloodSugar = todayData.bloodSugar.toFloat(),
                            recordDate = todayData.recordDate
                        ))
                        Log.d("HealthStatusScreen", "${day}일차 오늘 데이터: 체중=${todayData.weight}")
                    }
                    else -> {
                        // 데이터가 없는 경우 - null 값으로 HealthData 생성
                        weeklyData.add(HealthData(
                            day = (day - weekStartDay) + 1, // 주차 내 1~7일
                            weight = null,
                            bloodPressureHigh = null,
                            bloodPressureLow = null,
                            bloodSugar = null,
                            recordDate = targetDateString
                        ))
                        Log.d("HealthStatusScreen", "${day}일차 데이터 없음 - 빈 값으로 처리")
                    }
                }
            }

            Log.d("HealthStatusScreen", "최종 데이터 개수: ${weeklyData.size}")
            weeklyData.toList()
        }
    }

    // 초기 로딩 및 에러 처리 (DiaryDetailScreen과 동일)
    LaunchedEffect(Unit) {
        Log.d("HealthStatusScreen", "HomeViewModel 데이터 로드 시작")
        homeViewModel.refreshProfile()
        healthViewModel.loadHealthHistory()
    }

    // 화면이 다시 보일 때마다 데이터 새로고침 (등록 후 돌아올 때)
    LaunchedEffect(navController.currentBackStackEntry) {
        Log.d("HealthStatusScreen", "화면 포커스 - 데이터 새로고침")
        healthViewModel.loadHealthHistory()
    }

    // HomeViewModel 데이터 로딩 상태 확인
    LaunchedEffect(menstrualDate, weekStartDay, weekEndDay) {
        Log.d("HealthStatusScreen", "HomeViewModel 데이터 변경:")
        Log.d("HealthStatusScreen", "  - menstrualDate: $menstrualDate")
        Log.d("HealthStatusScreen", "  - weekStartDay: $weekStartDay")
        Log.d("HealthStatusScreen", "  - weekEndDay: $weekEndDay")
    }

    // 에러 메시지 자동 클리어
    LaunchedEffect(healthState.errorMessage) {
        if (healthState.errorMessage != null) {
            kotlinx.coroutines.delay(5000)
            healthViewModel.clearError()
        }
    }

    // 현재 선택된 건강 지표의 평균값 계산
    val currentAverage = calculateAverage(healthDataList, selectedHealthType)

    // 오늘 날짜의 데이터가 이미 존재하는지 확인
    val today = LocalDate.now().toString()
    val hasTodayData = remember(healthDataList, today) {
        val todayData = healthDataList.find { it.recordDate == today }
        val hasData = todayData != null && (
            todayData.weight != null ||
            todayData.bloodPressureHigh != null ||
            todayData.bloodSugar != null
        )
        Log.d("HealthStatusScreen", "🔍 오늘 날짜: $today")
        Log.d("HealthStatusScreen", "🔍 전체 healthDataList: ${healthDataList.map { "${it.recordDate}: weight=${it.weight}, bp=${it.bloodPressureHigh}/${it.bloodPressureLow}, bs=${it.bloodSugar}" }}")
        Log.d("HealthStatusScreen", "🔍 찾은 todayData: $todayData")
        Log.d("HealthStatusScreen", "🔍 오늘 날짜($today) 데이터 존재: $hasData")
        hasData
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // TopAppBar with back button and + button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            if (hasTodayData) {
                // 오늘 데이터가 있으면 수정 버튼 표시
                IconButton(
                    onClick = {
                        // 오늘 날짜 데이터를 찾아서 수정 모드로 이동
                        val todayData = healthDataList.find { it.recordDate == today }
                        Log.d("HealthStatusScreen", "수정 버튼 클릭 - 오늘 데이터: ${todayData?.recordDate}")
                        Log.d("HealthStatusScreen", "수정 버튼 클릭 - 체중: ${todayData?.weight}, 혈압: ${todayData?.bloodPressureHigh}/${todayData?.bloodPressureLow}, 혈당: ${todayData?.bloodSugar}")

                        if (todayData != null) {
                            // 기존 데이터가 있으면 수정 모드로 ViewModel에 데이터 설정
                            Log.d("HealthStatusScreen", "수정 모드 - ViewModel에 데이터 설정: $todayData")
                            healthViewModel.setEditingDataFromHealthData(todayData)
                            navController.navigate("health_register")
                        } else {
                            // 데이터가 없으면 일반 등록 모드
                            healthViewModel.clearEditingData()
                            navController.navigate("health_register")
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "건강 데이터 수정",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                // 오늘 데이터가 없으면 추가 버튼 표시
                IconButton(
                    onClick = { navController.navigate("health_register?isEdit=false") },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "건강 데이터 추가",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .pointerInput(selectedHealthType) {
                    var totalDragAmount = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragAmount = 0f },
                        onDragEnd = {
                            // 드래그가 끝났을 때만 섹션 변경
                            if (abs(totalDragAmount) > 100f) { // 100dp 이상 드래그했을 때
                                val healthTypes = HealthType.values()
                                val currentIndex = healthTypes.indexOf(selectedHealthType)

                                when {
                                    // 왼쪽으로 스와이프 - 다음 타입
                                    totalDragAmount < 0 && currentIndex < healthTypes.size - 1 -> {
                                        val newType = healthTypes[currentIndex + 1]
                                        targetHealthType = newType
                                        selectedHealthType = newType
                                    }
                                    // 오른쪽으로 스와이프 - 이전 타입
                                    totalDragAmount > 0 && currentIndex > 0 -> {
                                        val newType = healthTypes[currentIndex - 1]
                                        targetHealthType = newType
                                        selectedHealthType = newType
                                    }
                                }
                            }
                        }
                    ) { _, dragAmount ->
                        totalDragAmount += dragAmount
                    }
                },
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 로딩 상태 표시
            if (healthState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFF49699)
                    )
                }
            }

            // 에러 메시지 표시
            healthState.errorMessage?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "오류: $error",
                        modifier = Modifier.padding(16.dp),
                        color = Color.Red,
                        fontSize = 14.sp
                    )
                }
            }

            // 데이터가 없을 때 표시
            if (!healthState.isLoading && healthDataList.isEmpty() && healthState.errorMessage == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = Color(0xFFCCCCCC),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "건강 데이터가 없습니다",
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = Color(0xFF666666)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "+ 버튼을 눌러 건강 데이터를 추가해보세요",
                            fontSize = 14.sp,
                            color = Color(0xFF999999),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 데이터가 있을 때만 차트와 선택기 표시
            if (healthDataList.isNotEmpty()) {
                // 통계 차트 (애니메이션 적용)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            translationX = slideOffset
                            alpha = if (abs(slideOffset) < 50f) 1f else 0.7f
                        }
                ) {
                    HealthStatisticsChart(
                        healthDataList = healthDataList,
                        selectedType = selectedHealthType,
                        selectedDataPoint = selectedDataPoint,
                        onDataPointClick = { dataPoint ->
                            selectedDataPoint = dataPoint
                        },
                        todayHealthData = healthState.todayHealthData,
                        menstrualDate = menstrualDate
                    )
                }

                // 건강 지표 선택 버튼들
                HealthTypeSelector(
                    healthDataList = healthDataList,
                    selectedType = selectedHealthType,
                    onTypeSelected = {
                        targetHealthType = it
                        selectedHealthType = it
                    },
                    onDataPointClick = { dataPoint ->
                        selectedDataPoint = dataPoint
                    },
                    menstrualDate = menstrualDate,
                    healthState = healthState,
                    homeViewModel = homeViewModel,
                    healthViewModel = healthViewModel
                )

                // 선택된 데이터 점 정보 표시
                selectedDataPoint?.let { dataPoint ->
                    SelectedDataPointInfo(
                        dataPoint = dataPoint,
                        selectedType = selectedHealthType,
                        menstrualDate = menstrualDate,
                        onDismiss = { selectedDataPoint = null },
                        onEdit = {
                            // 선택된 데이터 점의 정보로 수정 모드로 ViewModel에 데이터 설정
                            Log.d("HealthStatusScreen", "선택된 데이터 수정 - ViewModel에 데이터 설정: $dataPoint")
                            healthViewModel.setEditingDataFromHealthData(dataPoint)
                            navController.navigate("health_register")
                        },
                        onDelete = {
                            // 삭제 확인 다이얼로그 표시
                            showDeleteDialog = true
                        }
                    )
                }
            }
        }
    }

    // 삭제 확인 다이얼로그
    if (showDeleteDialog && selectedDataPoint != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    "데이터 삭제",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("${formatPregnancyWeeks(selectedDataPoint!!.recordDate, menstrualDate, "${selectedDataPoint!!.day}일차")} 데이터를 삭제하시겠습니까?\n삭제된 데이터는 복구할 수 없습니다.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        // TODO: 서버 데이터 삭제 기능 - maternalId 매핑 필요
                        // 현재는 임시로 로컬에서만 제거
                        selectedDataPoint = null
                        showDeleteDialog = false
                        // 실제 구현 시: healthViewModel.deleteHealthRecord(maternalId)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF44336)
                    )
                ) {
                    Text("삭제", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false }
                ) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
fun HealthTypeSelector(
    healthDataList: List<HealthData>,
    selectedType: HealthType,
    onTypeSelected: (HealthType) -> Unit,
    onDataPointClick: (HealthData) -> Unit,
    menstrualDate: String?,
    healthState: com.ms.helloworld.viewmodel.HealthState,
    homeViewModel: HomeViewModel,
    healthViewModel: HealthViewModel
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HealthType.values().forEach { type ->
            val isSelected = selectedType == type
            val typeAverage = calculateAverage(healthDataList, type)

            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp)
                    .clickable {
                        if (selectedType == type) {
                            // 이미 선택된 타입을 다시 클릭하면 가장 최근 데이터로 이동
                            Log.d("HealthStatusScreen", "더블클릭 - 가장 최근 데이터 찾기")
                            Log.d("HealthStatusScreen", "더블클릭 - 현재 healthDataList 날짜들: ${healthDataList.map { it.recordDate }}")

                            // 현재 화면의 데이터 중에서 가장 최근 데이터 찾기
                            val latestData = healthDataList
                                .filter { data ->
                                    // 선택된 타입에 해당하는 데이터가 있는지 확인
                                    when (selectedType) {
                                        HealthType.WEIGHT -> data.weight != null
                                        HealthType.BLOOD_PRESSURE -> data.bloodPressureHigh != null
                                        HealthType.BLOOD_SUGAR -> data.bloodSugar != null
                                    }
                                }
                                .maxByOrNull { it.recordDate ?: "" }

                            if (latestData != null) {
                                Log.d("HealthStatusScreen", "더블클릭 - 최근 데이터 발견: ${latestData.recordDate}")
                                onDataPointClick(latestData)
                                return@clickable
                            }

                            // 현재 화면에 데이터가 없으면 전체 서버 데이터에서 가장 최근 데이터 찾기
                            Log.d("HealthStatusScreen", "더블클릭 - 현재 화면에 데이터 없음, 전체 데이터에서 검색")

                            // 전체 서버 데이터에서 선택된 타입의 가장 최근 데이터 찾기
                            val allServerData = mutableListOf<Pair<String, Any>>()

                            // history 데이터 추가
                            healthState.healthHistory.forEach { item ->
                                val hasData = when (selectedType) {
                                    HealthType.WEIGHT -> item.weight.toFloat() > 0f
                                    HealthType.BLOOD_PRESSURE -> item.bloodPressure.isNotEmpty()
                                    HealthType.BLOOD_SUGAR -> item.bloodSugar > 0
                                }
                                if (hasData) {
                                    allServerData.add(item.recordDate to item)
                                }
                            }

                            // todayHealthData 추가
                            healthState.todayHealthData?.let { todayData ->
                                val hasData = when (selectedType) {
                                    HealthType.WEIGHT -> todayData.weight.toFloat() > 0f
                                    HealthType.BLOOD_PRESSURE -> todayData.bloodPressure.isNotEmpty()
                                    HealthType.BLOOD_SUGAR -> todayData.bloodSugar > 0
                                }
                                if (hasData) {
                                    allServerData.add(todayData.recordDate to todayData)
                                }
                            }

                            // 가장 최근 데이터 찾기
                            val latestServerData = allServerData.maxByOrNull { it.first }
                            if (latestServerData != null) {
                                Log.d("HealthStatusScreen", "더블클릭 - 서버에서 최근 데이터 발견: ${latestServerData.first}")
                                // 해당 날짜가 포함된 주차로 데이터 새로고침
                                homeViewModel.refreshProfile()
                                healthViewModel.loadHealthHistory()
                                return@clickable
                            }

                            Log.d("HealthStatusScreen", "더블클릭 - 전체 데이터에서 해당 타입의 데이터 없음")
                        } else {
                            onTypeSelected(type)
                        }
                    }
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color(0xFFF49699) else Color(0xFFE0E0E0),
                        shape = RoundedCornerShape(12.dp)
                    ),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) Color.White else Color(0xFFFAFAFA)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = type.displayName,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = typeAverage,
                        fontSize = if (isSelected) 14.sp else 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color(0xFFF49699) else Color.Gray,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun HealthStatisticsChart(
    healthDataList: List<HealthData>,
    selectedType: HealthType,
    selectedDataPoint: HealthData?,
    onDataPointClick: (HealthData) -> Unit,
    todayHealthData: MaternalHealthGetResponse? = null,
    menstrualDate: String? = null
) {
    val chartData = extractChartData(healthDataList, selectedType)
    val (minValue, maxValue) = calculateChartRange(chartData, selectedType)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Text(
                text = "통계",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 현재 날짜와 값 표시
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                val lastData = chartData.lastOrNull()
                if (lastData != null) {
                    // 실제 차트의 마지막 데이터 점에 해당하는 HealthData 찾기
                    val lastHealthData = healthDataList.find { it.day == lastData.first }
                    val pregnancyInfo = lastHealthData?.recordDate?.let { recordDate ->
                        Log.d("HealthStatusScreen", "통계 차트 - 실제 마지막 데이터 날짜: $recordDate")
                        formatPregnancyWeeks(recordDate, menstrualDate)
                    } ?: run {
                        // 실제 데이터가 없으면 오늘 날짜로 계산
                        val today = java.time.LocalDate.now().toString()
                        Log.d("HealthStatusScreen", "통계 차트 - 실제 데이터 없음, 오늘 날짜 사용: $today")
                        formatPregnancyWeeks(today, menstrualDate)
                    }

                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = getCurrentDateString(todayHealthData),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "$pregnancyInfo | ${lastData.second}${selectedType.unit}",
                            fontSize = 11.sp,
                            color = Color(0xFFF49699),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 차트 영역
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Y축 눈금
                YAxisLabels(
                    minValue = minValue,
                    maxValue = maxValue,
                    modifier = Modifier.align(Alignment.CenterStart)
                )

                // 차트 그래프
                LineChart(
                    data = chartData,
                    healthDataList = healthDataList,
                    selectedType = selectedType,
                    selectedDataPoint = selectedDataPoint,
                    onDataPointClick = onDataPointClick,
                    minValue = minValue,
                    maxValue = maxValue,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 40.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
                )
            }
        }
    }
}

@Composable
fun YAxisLabels(
    minValue: Float,
    maxValue: Float,
    modifier: Modifier = Modifier
) {
    val steps = 5
    val stepValue = (maxValue - minValue) / steps

    Column(
        modifier = modifier.height(300.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        for (i in steps downTo 0) {
            val value = minValue + (stepValue * i)
            Text(
                text = String.format("%.1f", value),
                fontSize = 10.sp,
                color = Color.Gray,
                modifier = Modifier.width(35.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
fun LineChart(
    data: List<Pair<Int, Float>>,
    healthDataList: List<HealthData>,
    selectedType: HealthType,
    selectedDataPoint: HealthData?,
    onDataPointClick: (HealthData) -> Unit,
    minValue: Float,
    maxValue: Float,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (data.isNotEmpty()) {
            val chartWidth = 280.dp
            val chartHeight = 300.dp

            // 차트 배경 박스 (모눈종이 격자 포함)
            Box(
                modifier = Modifier
                    .size(chartWidth, chartHeight)
                    .background(Color.White)
            ) {
                // 가로 격자선 (세밀한 모눈종이 효과)
                for (i in 0..20) {
                    val yPosition = (i * chartHeight.value / 20).dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(Color(0xFFF0F0F0))
                            .offset(y = yPosition)
                    )
                }
                
                // 세로 격자선 (세밀한 모눈종이 효과)
                for (i in 0..28) {
                    val xPosition = (i * chartWidth.value / 28).dp
                    Box(
                        modifier = Modifier
                            .width(0.5.dp)
                            .fillMaxHeight()
                            .background(Color(0xFFF0F0F0))
                            .offset(x = xPosition)
                    )
                }
                
                // 주요 가로선 (값 구분선)
                for (i in 0..5) {
                    val yPosition = (i * chartHeight.value / 5).dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFD0D0D0))
                            .offset(y = yPosition)
                    )
                }
                
                // 주요 세로선 (일별 구분선)
                for (i in 0..6) {
                    val xPosition = (i * chartWidth.value / 6).dp
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(Color(0xFFD0D0D0))
                            .offset(x = xPosition)
                    )
                }
            }

            // 연결선 그리기 (Box 방식으로 단순화)
            data.zipWithNext().forEach { (current, next) ->
                val (currentDay, currentValue) = current
                val (nextDay, nextValue) = next

                // 1~7일을 0~6 위치에 배치 (7일을 6개 간격으로 나누기)
                val currentX = ((currentDay - 1) * chartWidth.value / 6).dp
                val currentY = ((maxValue - currentValue) / (maxValue - minValue) * chartHeight.value).dp
                val nextX = ((nextDay - 1) * chartWidth.value / 6).dp
                val nextY = ((maxValue - nextValue) / (maxValue - minValue) * chartHeight.value).dp
                
                val lineWidth = kotlin.math.sqrt(
                    ((nextX.value - currentX.value) * (nextX.value - currentX.value) + 
                     (nextY.value - currentY.value) * (nextY.value - currentY.value)).toDouble()
                ).dp
                
                val angle = kotlin.math.atan2(
                    nextY.value - currentY.value,
                    nextX.value - currentX.value
                ) * 180 / kotlin.math.PI
                
                Box(
                    modifier = Modifier
                        .width(lineWidth)
                        .height(2.dp)
                        .offset(
                            x = currentX,
                            y = currentY
                        )
                        .graphicsLayer {
                            rotationZ = angle.toFloat()
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        }
                        .background(Color.LightGray)
                )
            }

            // 데이터 점들 (격자 위에 배치)
            data.forEachIndexed { index, (day, value) ->
                // 1~7일을 0~6 위치에 배치
                val xPosition = ((day - 1) * chartWidth.value / 6).dp
                val yPosition = ((maxValue - value) / (maxValue - minValue) * chartHeight.value).dp
                
                // 해당 일자의 HealthData 찾기
                val currentHealthData = healthDataList.find { it.day == day }
                val isSelected = selectedDataPoint?.day == day

                // 데이터 점 (클릭 가능)
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 12.dp else 8.dp)
                        .offset(
                            x = xPosition - if (isSelected) 6.dp else 4.dp, 
                            y = yPosition - if (isSelected) 6.dp else 4.dp
                        )
                        .clip(CircleShape)
                        .background(
                            if (isSelected) Color(0xFFE91E63) else Color(0xFFF49699)
                        )
                        .clickable { 
                            currentHealthData?.let { onDataPointClick(it) }
                        }
                )

                // 값 표시 (모든 점에 표시)
                Text(
                    text = String.format("%.1f", value),
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.Black else Color.Gray,
                    modifier = Modifier
                        .offset(x = xPosition - 20.dp, y = yPosition - 25.dp)
                        .width(40.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SelectedDataPointInfo(
    dataPoint: HealthData,
    selectedType: HealthType,
    menstrualDate: String?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${formatPregnancyWeeks(dataPoint.recordDate, menstrualDate, "${dataPoint.day}일차")} 데이터",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "닫기",
                        tint = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 날짜 정보
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = "날짜",
                    modifier = Modifier.size(16.dp),
                    tint = Color.Gray
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatPregnancyWeeks(dataPoint.recordDate, menstrualDate, "${dataPoint.day}일차"),
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 선택된 건강 지표별 상세 정보
            when (selectedType) {
                HealthType.WEIGHT -> {
                    dataPoint.weight?.let { weight ->
                        DetailInfoRow(
                            label = "체중",
                            value = "${weight}kg",
                            icon = Icons.Default.Person
                        )
                    }
                }
                HealthType.BLOOD_PRESSURE -> {
                    Row {
                        dataPoint.bloodPressureHigh?.let { high ->
                            DetailInfoRow(
                                label = "수축기 혈압",
                                value = "${high}mmHg",
                                icon = Icons.Default.Favorite,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        dataPoint.bloodPressureLow?.let { low ->
                            DetailInfoRow(
                                label = "이완기 혈압",
                                value = "${low}mmHg",
                                icon = Icons.Default.FavoriteBorder,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                HealthType.BLOOD_SUGAR -> {
                    dataPoint.bloodSugar?.let { sugar ->
                        DetailInfoRow(
                            label = "혈당",
                            value = "${sugar}mg/dL",
                            icon = Icons.Default.Info
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 수정/삭제 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 수정 버튼
                Button(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "수정",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("수정")
                }

                // 삭제 버튼
                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF44336)
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "삭제",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("삭제")
                }
            }
        }
    }
}

@Composable
fun DetailInfoRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = Color(0xFFF49699)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color.Gray
            )
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
    }
}

// 유틸리티 함수들
fun calculateAverage(healthDataList: List<HealthData>, type: HealthType): String {
    val values = when (type) {
        HealthType.WEIGHT -> healthDataList.mapNotNull { it.weight }
        HealthType.BLOOD_PRESSURE -> healthDataList.mapNotNull { it.bloodPressureHigh }
        HealthType.BLOOD_SUGAR -> healthDataList.mapNotNull { it.bloodSugar }
    }

    val average = if (values.isNotEmpty()) values.average().toFloat() else 0f
    return "${String.format("%.1f", average)}${type.unit}"
}

fun extractChartData(healthDataList: List<HealthData>, type: HealthType): List<Pair<Int, Float>> {
    return when (type) {
        HealthType.WEIGHT -> healthDataList.mapNotNull { data ->
            data.weight?.let { data.day to it }
        }
        HealthType.BLOOD_PRESSURE -> healthDataList.mapNotNull { data ->
            data.bloodPressureHigh?.let { data.day to it }
        }
        HealthType.BLOOD_SUGAR -> healthDataList.mapNotNull { data ->
            data.bloodSugar?.let { data.day to it }
        }
    }
}

fun calculateChartRange(data: List<Pair<Int, Float>>, type: HealthType): Pair<Float, Float> {
    if (data.isEmpty()) return Pair(0f, 100f)

    val values = data.map { it.second }
    val minData = values.minOrNull() ?: 0f
    val maxData = values.maxOrNull() ?: 100f

    // 범위를 조금 여유있게 설정
    val range = maxData - minData
    val padding = max(range * 0.1f, 5f)

    return Pair(
        max(minData - padding, 0f),
        maxData + padding
    )
}

fun getCurrentDateString(healthData: MaternalHealthGetResponse?): String {
    return if (healthData != null) {
        val date = healthData.recordDate // YYYY-MM-DD 형식
        val formattedDate = try {
            // YYYY-MM-DD를 YY.MM.DD 형식으로 변환
            val parts = date.split("-")
            if (parts.size == 3) {
                "${parts[0].takeLast(2)}.${parts[1]}.${parts[2]}"
            } else {
                date
            }
        } catch (e: Exception) {
            date
        }
        "$formattedDate"
    } else {
        "데이터 없음"
    }
}

fun formatDateString(recordDate: String?, day: Int): String {
    return if (recordDate != null) {
        try {
            // YYYY-MM-DD를 파싱하여 한국어 형식으로 변환
            val parts = recordDate.split("-")
            if (parts.size == 3) {
                val year = parts[0]
                val month = parts[1].toInt()
                val dayOfMonth = parts[2].toInt()
                "${year}년 ${month}월 ${dayOfMonth}일"
            } else {
                "${day}일차"
            }
        } catch (e: Exception) {
            "${day}일차"
        }
    } else {
        "${day}일차"
    }
}

// 임신 주차와 일차를 계산하는 함수
data class PregnancyWeeks(
    val week: Int,
    val day: Int
) {
    override fun toString(): String = "${week}주 ${day}일"
}

fun calculatePregnancyWeeks(recordDate: String, lastMenstrualPeriod: String? = null): PregnancyWeeks {
    return try {
        // 마지막 생리일이 없으면 기본값 반환
        val lmpDate = if (lastMenstrualPeriod != null) {
            LocalDate.parse(lastMenstrualPeriod, DateTimeFormatter.ISO_LOCAL_DATE)
        } else {
            Log.e("HealthStatusScreen", "마지막 생리일이 null입니다")
            return PregnancyWeeks(0, 0)
        }

        val currentDate = LocalDate.parse(recordDate, DateTimeFormatter.ISO_LOCAL_DATE)
        val daysDifference = ChronoUnit.DAYS.between(lmpDate, currentDate).toInt()

        // 임신 주차 계산
        // 252일차 = 36주 7일이 되도록 계산
        // 246~252일차 = 36주 1~7일
        val weeks = if (daysDifference % 7 == 0) {
            daysDifference / 7  // 7로 나누어떨어지는 경우
        } else {
            (daysDifference / 7) + 1  // 나머지가 있는 경우
        }

        val days = if (daysDifference % 7 == 0) {
            7  // 7로 나누어떨어지는 경우 7일
        } else {
            daysDifference % 7  // 나머지
        }

        Log.d("HealthStatusScreen", "임신 주차 계산: ${recordDate} -> ${weeks}주 ${days}일 (${daysDifference}일차)")
        Log.d("HealthStatusScreen", "계산 상세: ${daysDifference}/7+1=${weeks}, ${daysDifference}%7+1=${days}")
        PregnancyWeeks(weeks, days)
    } catch (e: Exception) {
        Log.e("HealthStatusScreen", "임신 주차 계산 오류: ${e.message}")
        PregnancyWeeks(0, 0)
    }
}

fun formatPregnancyWeeks(recordDate: String?, lastMenstrualPeriod: String? = null, fallbackText: String = "미정"): String {
    return if (recordDate != null) {
        val PregnancyWeeks = calculatePregnancyWeeks(recordDate, lastMenstrualPeriod)
        "임신 ${PregnancyWeeks.week}주 ${PregnancyWeeks.day}일"
    } else {
        fallbackText
    }
}

@Preview(showBackground = true)
@Composable
fun HealthStatusScreenPreview() {
    HealthStatusScreen(navController = null as NavHostController)
}