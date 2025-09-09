package com.ms.helloworld.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

// 데이터 클래스들
data class FetalMovementData(
    val dailyData: List<Pair<String, Float>>,
    val weeklyAverage: Float,
    val weeklyChange: Float
)

data class ContractionData(
    val totalToday: Int,
    val averageInterval: Int,
    val timelineData: List<ContractionEvent>,
    val dailyMessage: String
)

data class ContractionEvent(
    val createAt: Long, // 진통 시작 시간 (milliseconds)
    val stop: Long,     // 진통 종료 시간 (milliseconds)
    val timestamp: String = ""
)

@SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    navController: NavHostController
) {
    val backgroundColor = Color(0xFFF5F5F5)

    // 상태 관리
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("태동 기록", "진통 기록")

    // 데이터 상태 (실제로는 ViewModel이나 Repository에서 가져올 데이터)
    var fetalMovementData by remember {
        mutableStateOf(
            FetalMovementData(
                dailyData = listOf(
                    Pair("월", 8f),
                    Pair("화", 6f),
                    Pair("수", 10f),
                    Pair("목", 9f),
                    Pair("금", 3f),
                    Pair("토", 7f),
                    Pair("일", 5f)
                ),
                weeklyAverage = 6.9f,
                weeklyChange = 1.2f
            )
        )
    }

    var contractionData by remember {
        mutableStateOf(
            ContractionData(
                totalToday = 12,
                averageInterval = 4,
                timelineData = listOf(
                    ContractionEvent(
                        System.currentTimeMillis() - 3600000L + 300000L,
                        System.currentTimeMillis() - 3600000L + 350000L
                    ), // 5분 위치, 50초 지속
                    ContractionEvent(
                        System.currentTimeMillis() - 3600000L + 900000L,
                        System.currentTimeMillis() - 3600000L + 980000L
                    ), // 15분 위치, 80초 지속
                    ContractionEvent(
                        System.currentTimeMillis() - 3600000L + 1800000L,
                        System.currentTimeMillis() - 3600000L + 1860000L
                    ), // 30분 위치, 60초 지속
                    ContractionEvent(
                        System.currentTimeMillis() - 3600000L + 2400000L,
                        System.currentTimeMillis() - 3600000L + 2510000L
                    ), // 40분 위치, 110초 지속
                    ContractionEvent(
                        System.currentTimeMillis() - 3600000L + 3000000L,
                        System.currentTimeMillis() - 3600000L + 3045000L
                    ), // 50분 위치, 45초 지속
                ),
                dailyMessage = "어떤 진통을 기록해 보신 것을 자주 겪는\n기록이야 좀더 해 좋습니다. 많이 잘 한것은\n해보신것이니까요. 그런일이죠?"
            )
        )
    }

    // 로딩 상태
    var isLoading by remember { mutableStateOf(false) }

    // 탭 변경 시 데이터 새로고침 함수
    fun refreshData(tabIndex: Int) {
        isLoading = true
        // 실제 구현에서는 여기서 API 호출이나 데이터베이스 쿼리
        when (tabIndex) {
            0 -> {
                // 태동 데이터 새로고침
                // fetalMovementData = repository.getFetalMovementData()
            }

            1 -> {
                // 진통 데이터 새로고침
                // contractionData = repository.getContractionData()
            }
        }
        isLoading = false
    }

    // 초기 데이터 로드
    LaunchedEffect(Unit) {
        refreshData(selectedTab)
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "임신 통계",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = Color.Black
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 커스텀 탭 버튼들
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    Button(
                        onClick = {
                            selectedTab = index
                            refreshData(index) // 탭 변경 시 데이터 새로고침
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == index) Color(0xFFFF6B9D) else Color.Transparent,
                            contentColor = if (selectedTab == index) Color.White else Color.Gray
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = if (selectedTab == index) 2.dp else 0.dp
                        )
                    ) {
                        Text(
                            text = title,
                            fontSize = 14.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // 로딩 표시 또는 콘텐츠
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFFF6B9D))
                }
            } else {
                // 탭 내용
                when (selectedTab) {
                    0 -> FetalMovementContent(
                        data = fetalMovementData,
                        onRefresh = { refreshData(0) }
                    )

                    1 -> ContractionContent(
                        data = contractionData,
                        onRefresh = { refreshData(1) }
                    )
                }
            }
        }
    }
}

@Composable
fun FetalMovementContent(
    data: FetalMovementData,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 새로고침 버튼
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onRefresh() },
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E8))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "새로고침",
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF4CAF50)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "데이터 새로고침",
                    fontSize = 12.sp,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 일별 태동 횟수 카드 (크기 확대)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "일별 태동 횟수",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "최근 일주일간의 태동 횟수를 표시합니다",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 바 차트
                FetalMovementBarChart(data.dailyData)
            }
        }

        // 통계 카드들
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 주간 평균 카드
            StatCard(
                modifier = Modifier.weight(1f),
                value = data.weeklyAverage.toString(),
                label = "주간 평균"
            )

            // 지난주 대비 카드
            StatCard(
                modifier = Modifier.weight(1f),
                value = "+${data.weeklyChange}",
                label = "지난주 대비",
                valueColor = Color(0xFF4CAF50)
            )
        }
    }
}

@Composable
fun ContractionContent(
    data: ContractionData,
    onRefresh: () -> Unit
) {
    // 선택된 점 상태를 ContractionContent 레벨로 이동
    var selectedPoint by remember { mutableStateOf<ContractionEvent?>(null) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { selectedPoint = null }, // 전체 화면 클릭으로 카드 닫기
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 새로고침 버튼
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onRefresh() },
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE8F0))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "새로고침",
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFFFF6B9D)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "데이터 새로고침",
                    fontSize = 12.sp,
                    color = Color(0xFFFF6B9D),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 오늘 일정 카드
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFF6B9D)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "주의",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = data.dailyMessage,
                    fontSize = 12.sp,
                    color = Color.White,
                    lineHeight = 16.sp
                )
            }
        }

        // 진통 간격 측정
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "진통 간격 측정",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Text(
                    text = "최근 1시간의 진통 간격을 표시합니다",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 진통 타임라인
                ContractionTimeline(
                    averageInterval = data.averageInterval, 
                    events = data.timelineData,
                    selectedPoint = selectedPoint,
                    onPointSelected = { selectedPoint = it }
                )
            }
        }

    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    valueColor: Color = Color.Black
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun FetalMovementBarChart(data: List<Pair<String, Float>>) {
    val maxValue = data.maxOf { it.second }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp), // 차트 높이 더 증가
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            data.forEach { (day, value) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f) // 각 막대가 동일한 공간 차지
                ) {
                    // 값 표시
                    Text(
                        text = value.toInt().toString(),
                        fontSize = 14.sp, // 폰트 크기 증가
                        fontWeight = FontWeight.Medium,
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    // 막대 - 더 크고 넓게
                    Box(
                        modifier = Modifier
                            .width(32.dp) // 막대 너비 증가
                            .height((value / maxValue * 140).dp) // 최대 높이 증가
                            .background(
                                Color(0xFF6BB6FF),
                                RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp) // 모서리 더 둥글게
                            )
                    )

                    Spacer(modifier = Modifier.height(12.dp)) // 간격 증가

                    // 요일 표시
                    Text(
                        text = day,
                        fontSize = 13.sp, // 폰트 크기 증가
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun ContractionTimeline(
    averageInterval: Int, 
    events: List<ContractionEvent>,
    selectedPoint: ContractionEvent?,
    onPointSelected: (ContractionEvent?) -> Unit
) {
    // 점들 사이의 간격으로 실제 평균 진통 간격 계산
    val calculatedAverageInterval = if (events.size > 1) {
        val sortedEvents = events.sortedBy { it.createAt }
        val intervals = mutableListOf<Long>()
        for (i in 1 until sortedEvents.size) {
            val intervalMs = sortedEvents[i].createAt - sortedEvents[i - 1].stop
            intervals.add(intervalMs / 1000 / 60) // 분 단위로 변환
        }
        if (intervals.isNotEmpty()) intervals.average().toInt() else averageInterval
    } else {
        averageInterval
    }
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 점 그래프를 맨 위에 배치
        ContractionScatterChart(
            events = events,
            selectedPoint = selectedPoint,
            onPointSelected = onPointSelected
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 최근 평균 간격
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "최근 평균 간격",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Text(
                text = "${calculatedAverageInterval}분",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 오늘 전체 횟수 표시를 맨 아래에 배치
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "오늘 전체 횟수",
                fontSize = 12.sp,
                color = Color.Gray,
            )
            Text(
                text = "${events.size}회",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
    }
}

@Composable
fun ContractionScatterChart(
    events: List<ContractionEvent>,
    selectedPoint: ContractionEvent?,
    onPointSelected: (ContractionEvent?) -> Unit
) {
    val maxX = 60 // x축 최대값 (간격 시간: 60분)
    val minY = 0 // y축 최소값 (0초)
    val maxY = 120 // y축 최대값 (120초 = 2분)
    val chartWidth = 280.dp
    val chartHeight = 220.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(chartHeight + 80.dp)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(chartWidth + 60.dp, chartHeight + 60.dp)
        ) {
            // y축 눈금 (세로) - 0초부터 120초까지 20초 단위
            Column(
                modifier = Modifier
                    .height(chartHeight)
                    .offset(x = 35.dp, y = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                for (i in maxY downTo minY step 20) {
                    Text(
                        text = "${i}초",
                        fontSize = 11.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.offset(x = (-30).dp)
                    )
                }
            }

            // x축 눈금 (가로) - 0분부터 60분까지 10분 단위
            Row(
                modifier = Modifier
                    .width(chartWidth)
                    .align(Alignment.BottomStart)
                    .offset(x = 35.dp, y = (-20).dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (i in 0..maxX step 10) {
                    Text(
                        text = "${i}분",
                        fontSize = 11.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 격자 영역
            Box(
                modifier = Modifier
                    .size(chartWidth, chartHeight)
                    .offset(x = 35.dp, y = 10.dp)
                    .background(Color(0xFFFAFAFA))
            ) {
                // 세로선 (10분 단위)
                for (i in 0..maxX step 10) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(Color(0xFFE0E0E0))
                            .offset(x = (i * chartWidth.value / maxX).dp)
                    )
                }

                // 가로선 (20초 단위)
                for (i in 0..((maxY - minY) / 20)) {
                    Box(
                        modifier = Modifier
                            .height(1.dp)
                            .fillMaxWidth()
                            .background(Color(0xFFE0E0E0))
                            .offset(y = (i * chartHeight.value / ((maxY - minY) / 20)).dp)
                    )
                }

                // 점들을 이어주는 선 그리기
                if (events.size > 1) {
                    val sortedEvents = events.sortedBy { it.createAt }
                    val startTime = events.minOf { it.createAt }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val path = Path()

                        sortedEvents.forEachIndexed { index, event ->
                            val timeFromStart = (event.createAt - startTime) / 1000 / 60
                            val xPos = timeFromStart.toFloat() / maxX * size.width
                            val durationSeconds = (event.stop - event.createAt) / 1000
                            val yPos =
                                (maxY - durationSeconds).toFloat() / (maxY - minY) * size.height

                            if (index == 0) {
                                path.moveTo(xPos, yPos)
                            } else {
                                path.lineTo(xPos, yPos)
                            }
                        }

                        drawPath(
                            path = path,
                            color = Color(0xFFFF6B9D),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                // 선 위에 진통 간격 텍스트 표시
                if (events.size > 1) {
                    val sortedEvents = events.sortedBy { it.createAt }
                    val startTime = events.minOf { it.createAt }

                    for (i in 1 until sortedEvents.size) {
                        val prevEvent = sortedEvents[i - 1]
                        val currentEvent = sortedEvents[i]

                        // 이전 점과 현재 점의 위치 계산
                        val prevTimeFromStart = (prevEvent.createAt - startTime) / 1000 / 60
                        val prevXPos = (prevTimeFromStart.toFloat() / maxX * chartWidth.value).dp
                        val prevDurationSeconds = (prevEvent.stop - prevEvent.createAt) / 1000
                        val prevYPos =
                            ((maxY - prevDurationSeconds).toFloat() / (maxY - minY) * chartHeight.value).dp

                        val currentTimeFromStart = (currentEvent.createAt - startTime) / 1000 / 60
                        val currentXPos =
                            (currentTimeFromStart.toFloat() / maxX * chartWidth.value).dp
                        val currentDurationSeconds =
                            (currentEvent.stop - currentEvent.createAt) / 1000
                        val currentYPos =
                            ((maxY - currentDurationSeconds).toFloat() / (maxY - minY) * chartHeight.value).dp

                        // 선의 중점에 간격 텍스트 표시
                        val midX = (prevXPos + currentXPos) / 2
                        val midY = (prevYPos + currentYPos) / 2

                        // 진통 간격 계산 (분 단위)
                        val intervalMinutes = (currentEvent.createAt - prevEvent.stop) / 1000 / 60

                        Box(
                            modifier = Modifier
                                .offset(x = midX, y = midY - 10.dp)
                                .background(
                                    Color.White.copy(alpha = 0.8f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${intervalMinutes}분",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFFF6B9D)
                            )
                        }
                    }
                }

                // 데이터 점들
                events.forEachIndexed { index, event ->
                    // x축: 한시간 동안의 절대 시간 위치 계산
                    val startTime = events.minOf { it.createAt }
                    val timeFromStart = (event.createAt - startTime) / 1000 / 60 // 분 단위
                    val xPosition = (timeFromStart.toFloat() / maxX * chartWidth.value).dp

                    // y축: 지속시간 계산 (초 단위)
                    val durationSeconds = (event.stop - event.createAt) / 1000
                    val yPosition =
                        ((maxY - durationSeconds).toFloat() / (maxY - minY) * chartHeight.value).dp

                    Box(
                        modifier = Modifier
                            .size(12.dp) // 클릭 영역을 위해 크기 증가
                            .offset(x = xPosition - 6.dp, y = yPosition - 6.dp)
                            .clickable { onPointSelected(event) }
                            .clip(CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .align(Alignment.Center)
                                .clip(CircleShape)
                                .background(
                                    if (selectedPoint == event) Color(0xFFFF1744)
                                    else Color(0xFFFF6B9D)
                                )
                        )
                    }
                }
            }

            // 축 라벨 - 지속시간을 간격시간 왼쪽에 배치
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-5).dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "지속 시간 (초)",
                    fontSize = 13.sp,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "간격 시간 (분)",
                    fontSize = 13.sp,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 선택된 점의 정보 표시
        selectedPoint?.let { point ->
            val durationSeconds = (point.stop - point.createAt) / 1000
            val sortedEvents = events.sortedBy { it.createAt }
            val currentIndex = sortedEvents.indexOf(point)

            // 이전 점과의 간격 계산
            val intervalText = if (currentIndex > 0) {
                val previousEvent = sortedEvents[currentIndex - 1]
                val intervalMinutes = (point.createAt - previousEvent.stop) / 1000 / 60
                "${intervalMinutes}분 간격"
            } else {
                "첫 번째 진통"
            }

            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 20.dp, y = 10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "진통 정보",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "지속시간: ${durationSeconds}초",
                        fontSize = 10.sp,
                        color = Color.Black
                    )
                    Text(
                        text = intervalText,
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RecordDetailScreenPreview() {
    RecordDetailScreen(navController = null as NavHostController)
}