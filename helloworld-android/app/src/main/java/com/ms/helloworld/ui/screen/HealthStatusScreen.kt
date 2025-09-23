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
import kotlin.math.max
import kotlin.math.abs

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
    val bloodSugar: Float?
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

    // 샘플 건강 데이터 (7일간) - mutable로 변경
    var healthDataList by remember {
        mutableStateOf(
            listOf(
                HealthData(1, 48.0f, 120f, 80f, 95f),
                HealthData(2, 49.0f, 125f, 82f, 102f),
                HealthData(3, 49.0f, 118f, 78f, 88f),
                HealthData(4, 50.0f, 130f, 85f, 110f),
                HealthData(5, 51.0f, 135f, 88f, 125f),
                HealthData(6, 52.0f, 128f, 83f, 98f),
                HealthData(7, 49.0f, 122f, 80f, 105f)
            )
        )
    }

    // 현재 선택된 건강 지표의 평균값 계산
    val currentAverage = calculateAverage(healthDataList, selectedHealthType)

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
            
            IconButton(
                onClick = { navController.navigate("health_register") },
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
                }
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
            }
        )

        // 선택된 데이터 점 정보 표시
        selectedDataPoint?.let { dataPoint ->
            SelectedDataPointInfo(
                dataPoint = dataPoint,
                selectedType = selectedHealthType,
                onDismiss = { selectedDataPoint = null },
                onEdit = { 
                    // HealthRegisterScreen으로 이동
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
                Text("${selectedDataPoint!!.day}일차 데이터를 삭제하시겠습니까?\n삭제된 데이터는 복구할 수 없습니다.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        // 데이터 삭제 실행
                        healthDataList = healthDataList.filter { it.day != selectedDataPoint!!.day }
                        selectedDataPoint = null
                        showDeleteDialog = false
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
    onDataPointClick: (HealthData) -> Unit
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
                    .height(75.dp)
                    .clickable { 
                        if (selectedType == type) {
                            // 이미 선택된 타입을 다시 클릭하면 오늘 데이터 표시
                            val todayData = healthDataList.lastOrNull()
                            todayData?.let { onDataPointClick(it) }
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
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = type.displayName,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = typeAverage,
                        fontSize = if (isSelected) 16.sp else 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color(0xFFF49699) else Color.Gray
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
    onDataPointClick: (HealthData) -> Unit
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
                    Text(
                        text = "${getCurrentDateString()} ${lastData.second}${selectedType.unit}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
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
                    text = "${dataPoint.day}일차 데이터",
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
                    text = "2024년 9월 ${dataPoint.day}일", // 실제 날짜 계산 필요
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

fun getCurrentDateString(): String {
    return "25.09.06 50.0kg" // 샘플 날짜
}

@Preview(showBackground = true)
@Composable
fun HealthStatusScreenPreview() {
    HealthStatusScreen(navController = null as NavHostController)
}