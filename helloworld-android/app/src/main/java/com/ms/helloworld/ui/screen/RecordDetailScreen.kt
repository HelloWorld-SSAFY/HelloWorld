package com.ms.helloworld.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ms.helloworld.dto.response.ContractionSession
import com.ms.helloworld.dto.response.FetalMovementRecord
import kotlinx.coroutines.launch
import com.ms.helloworld.ui.components.CustomTopAppBar
import com.ms.helloworld.ui.theme.MainColor
import com.ms.helloworld.viewmodel.WearableViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


// 데이터 클래스들
data class FetalMovementData(
    val dailyData: List<Pair<String, Float>>,
    val weeklyAverage: Float,
    val weeklyChange: Float
)

data class ContractionData(
    val totalRecent: Int,
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
@Composable
fun RecordDetailScreen(
    navController: NavHostController,
    wearableViewModel: WearableViewModel = hiltViewModel()
) {
    // ViewModel에서 실제 데이터 가져오기
    val contractions by wearableViewModel.contractions.collectAsState()
    val fetalMovements by wearableViewModel.fetalMovements.collectAsState()
    val isLoadingContractions by wearableViewModel.isLoading.collectAsState()
    val isLoadingFetal by wearableViewModel.isLoadingFetal.collectAsState()

    // 지난주 태동 데이터 가져오기
    val previousWeekFetalMovements by wearableViewModel.previousWeekFetalMovements.collectAsState()
    val isLoadingPreviousWeek by wearableViewModel.isLoadingPreviousWeekFetal.collectAsState()


    LaunchedEffect(Unit) {
        val now = LocalDateTime.now()

        // 최근 12시간 범위 계산
        val twelveHoursAgo = now.minusHours(12)
        val fromDateTime =
            twelveHoursAgo.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
        val toDateTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))

        // 태동은 여전히 주간 데이터 유지
        val today = LocalDate.now()
        val dayOfWeek = today.dayOfWeek.value % 7
        val startOfWeek = today.minusDays(dayOfWeek.toLong())
        val endOfWeek = startOfWeek.plusDays(6)
        val weekStartString = startOfWeek.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val weekEndString = endOfWeek.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        // 지난주 태동 범위
        val previousWeekStart = startOfWeek.minusDays(7)
        val previousWeekEnd = endOfWeek.minusDays(7)
        val previousWeekStartString =
            previousWeekStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val previousWeekEndString =
            previousWeekEnd.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        // 최근 12시간 진통 기록 로드
        wearableViewModel.loadContractions(from = fromDateTime, to = toDateTime)

        // 이번 주 태동 기록 로드
        wearableViewModel.loadFetalMovements(from = weekStartString, to = weekEndString)

        // 지난주 태동 기록 로드
        wearableViewModel.loadPreviousWeekFetalMovements(
            from = previousWeekStartString,
            to = previousWeekEndString
        )
    }

    // 실제 데이터로 계산된 값들
    val thisWeekFetalAverage = if (fetalMovements.isEmpty()) 0.0 else {
        fetalMovements.sumOf { it.totalCount }.toDouble() / fetalMovements.size
    }

    // 지난주 평균 계산
    val previousWeekAverage = if (previousWeekFetalMovements.isEmpty()) 0.0 else {
        previousWeekFetalMovements.sumOf { it.totalCount }
            .toDouble() / previousWeekFetalMovements.size
    }

    val weeklyChange = thisWeekFetalAverage - previousWeekAverage

    // 일별 데이터 변환 (API 데이터를 차트용 데이터로 변환)
    val dailyFetalData = convertToDaily(fetalMovements)

    val contractionData = remember(contractions) {
        ContractionData(
            totalRecent  = contractions.size,
            averageInterval = calculateAverageInterval(contractions),
            timelineData = contractions.mapNotNull { session ->
                try {
                    if (session.startTime.isNotBlank() && session.endTime.isNotBlank()) {
                        ContractionEvent(
                            createAt = parseISOToMillis(session.startTime),
                            stop = parseISOToMillis(session.endTime)
                        )
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null // 파싱 실패 시 해당 세션 제외
                }
            },
            dailyMessage = "진통 간격이 짧아지면 병원에 가보세요"
        )
    }

    val fetalMovementData = FetalMovementData(
        dailyData = dailyFetalData,
        weeklyAverage = thisWeekFetalAverage.toFloat(),
        weeklyChange = weeklyChange.toFloat()
    )

    // 상태 관리
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("태동 기록", "진통 기록")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    // 로딩 상태
    val isLoading = isLoadingContractions || isLoadingFetal || isLoadingPreviousWeek

    // 탭 변경 시 데이터 새로고침 함수
    fun refreshData(tabIndex: Int) {
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
    }

    // pager 상태와 selectedTab 동기화
    LaunchedEffect(pagerState.currentPage) {
        selectedTab = pagerState.currentPage
        refreshData(pagerState.currentPage)
    }

    // 초기 데이터 로드
    LaunchedEffect(Unit) {
        refreshData(selectedTab)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        CustomTopAppBar(
            title = "임신 통계",
            navController = navController
        )
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
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == index) {
                            // 태동 기록(0번)일 때는 파란색, 진통 기록(1번)일 때는 분홍색
                            if (index == 0) Color(0xFF6BB6FF) else MainColor
                        } else Color.Transparent,
                        contentColor = if (selectedTab == index) Color.White else Color.Gray
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = if (selectedTab != index) {
                        androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color.Gray.copy(alpha = 0.6f)
                        )
                    } else null,
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
                CircularProgressIndicator(color = MainColor)
            }
        } else {
            // HorizontalPager로 스와이프 가능한 탭 내용
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
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

// API 데이터를 일별 차트 데이터로 변환하는 함수 (null 안전성 추가)
@SuppressLint("NewApi")
fun convertToDaily(fetalMovements: List<FetalMovementRecord>): List<Pair<String, Float>> {
    val today = LocalDate.now()
    val dayOfWeek = today.dayOfWeek.value % 7
    val startOfWeek = today.minusDays(dayOfWeek.toLong())

    // 일주일간의 요일별 데이터 초기화
    val dailyData = mutableMapOf<String, Float>()
    val dayNames = listOf("일", "월", "화", "수", "목", "금", "토")

    for (i in 0..6) {
        dailyData[dayNames[i]] = 0f
    }

    // API 데이터를 요일별로 매핑
    fetalMovements.forEach { record ->
        try {
            if (record.recordedAt.isNotBlank() && record.recordedAt.length >= 10) {
                val recordDate = LocalDate.parse(record.recordedAt.substring(0, 10))
                val daysBetween =
                    java.time.temporal.ChronoUnit.DAYS.between(startOfWeek, recordDate).toInt()

                if (daysBetween in 0..6) {
                    val dayName = dayNames[daysBetween]
                    dailyData[dayName] = record.totalCount.toFloat()
                }
            }
        } catch (e: Exception) {
            // 날짜 파싱 실패 시 무시
        }
    }

    return dayNames.map { day -> Pair(day, dailyData[day] ?: 0f) }
}

// 진통 간격 평균 계산
fun calculateAverageInterval(contractions: List<ContractionSession>): Int {
    if (contractions.size <= 1) return 0

    return contractions.map { it.intervalMin }.average().toInt()
}

// ISO 날짜 문자열을 밀리초로 변환 (null 안전성 추가)
@SuppressLint("NewApi")
fun parseISOToMillis(isoString: String?): Long {
    if (isoString.isNullOrBlank()) {
        return System.currentTimeMillis()
    }

    return try {
        // ISO 문자열이 'Z'로 끝나지 않는 경우 처리
        val formattedString = if (isoString.endsWith('Z')) {
            isoString
        } else {
            "${isoString}Z" // UTC 표시 추가
        }

        val instant = java.time.Instant.parse(formattedString)
        instant.toEpochMilli()
    } catch (e: Exception) {
        // 파싱 실패 시 현재 시간 반환
        System.currentTimeMillis()
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
                value = "${data.weeklyAverage}회",
                label = "주간 평균"
            )

            // 지난주 대비 카드
            StatCard(
                modifier = Modifier.weight(1f),
                value = "+${data.weeklyChange}회",
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

        // 오늘 일정 카드
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MainColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "주의",
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "주의",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

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
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Text(
                    text = "최근 12시간의 진통 간격을 표시합니다",
                    fontSize = 14.sp,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = valueColor
            )
            Spacer(modifier = Modifier.height(8.dp))
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
                fontSize = 14.sp,
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
                text = "최근 12시간 횟수",
                fontSize = 14.sp,
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
    // 현재 시간과 12시간 전 시간 계산
    val currentTime = System.currentTimeMillis()
    val twelveHoursAgo = currentTime - (12 * 60 * 60 * 1000) // 12시간 전

    // 최근 12시간 내의 데이터만 필터링
    val filteredEvents = remember(events) {
        events.filter { event ->
            event.createAt >= twelveHoursAgo && event.createAt <= currentTime
        }
    }

    val maxX = 12 // x축 최대값 (12시간)
    val minY = 0 // y축 최소값 (0초)
    val maxY = 120 // y축 최대값 (120초 = 2분)
    val chartWidth = 480.dp // 차트 폭을 더 넓게 설정
    val chartHeight = 240.dp

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 데이터 없을 때 메시지 표시
        if (filteredEvents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight + 80.dp)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "최근 12시간 내 진통 기록이 없습니다",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        } else {
            // LazyRow로 가로 스크롤 가능한 차트
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight + 80.dp)
                    .background(Color.White),
                horizontalArrangement = Arrangement.Start
            ) {
                item {
                    Box(
                        modifier = Modifier.size(chartWidth + 80.dp, chartHeight + 80.dp)
                    ) {
                        // y축 눈금 (세로) - 0초부터 120초까지 20초 단위
                        Column(
                            modifier = Modifier
                                .height(chartHeight)
                                .offset(x = 50.dp, y = 20.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (i in maxY downTo minY step 20) {
                                Text(
                                    text = "${i}초",
                                    fontSize = 11.sp,
                                    color = Color.Black,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.End,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .offset(x = (-45).dp)
                                        .width(40.dp)
                                )
                            }
                        }

                        // x축 눈금 - 현재부터 12시간전까지 (왼쪽에서 오른쪽으로)
                        Row(
                            modifier = Modifier
                                .width(chartWidth)
                                .align(Alignment.BottomStart)
                                .offset(x = 50.dp, y = (-20).dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (i in 0..maxX step 2) {
                                val timeLabel = if (i == 0) "현재" else "${i}시간전"
                                Text(
                                    text = timeLabel,
                                    fontSize = 10.sp,
                                    color = Color.Black,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // 격자 영역
                        Box(
                            modifier = Modifier
                                .size(chartWidth, chartHeight)
                                .offset(x = 50.dp, y = 20.dp)
                                .background(Color(0xFFFAFAFA))
                        ) {
                            // 세로선 (2시간 단위)
                            for (i in 0..maxX step 2) {
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(
                                            if (i == 0) Color(0xFFFF6B9D).copy(alpha = 0.5f) // 현재 시간선(맨 왼쪽) 강조
                                            else Color(0xFFE0E0E0)
                                        )
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
                            if (filteredEvents.size > 1) {
                                val sortedEvents = filteredEvents.sortedBy { it.createAt }
                                val twelveHoursInMs = 12 * 60 * 60 * 1000f

                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val path = Path()

                                    sortedEvents.forEachIndexed { index, event ->
                                        // 현재 시간을 기준점(0)으로 하여 과거로 갈수록 오른쪽으로 이동
                                        val timeFromCurrent = (currentTime - event.createAt).toFloat()
                                        val xPos = (timeFromCurrent / twelveHoursInMs) * size.width

                                        val durationSeconds = (event.stop - event.createAt) / 1000
                                        val yPos = (maxY - durationSeconds).toFloat() / (maxY - minY) * size.height

                                        if (index == 0) {
                                            path.moveTo(xPos, yPos)
                                        } else {
                                            path.lineTo(xPos, yPos)
                                        }
                                    }

                                    drawPath(
                                        path = path,
                                        color = Color(0xFFFF6B9D),
                                        style = Stroke(width = 3.dp.toPx())
                                    )
                                }
                            }

                            // 선 위에 진통 간격 텍스트 표시
                            if (filteredEvents.size > 1) {
                                val sortedEvents = filteredEvents.sortedBy { it.createAt }
                                val twelveHoursInMs = 12 * 60 * 60 * 1000f

                                for (i in 1 until sortedEvents.size) {
                                    val prevEvent = sortedEvents[i - 1]
                                    val currentEvent = sortedEvents[i]

                                    // 현재 시간 기준으로 위치 계산 (과거일수록 오른쪽)
                                    val prevTimeFromCurrent = (currentTime - prevEvent.createAt).toFloat()
                                    val currentTimeFromCurrent = (currentTime - currentEvent.createAt).toFloat()

                                    val prevXPos = ((prevTimeFromCurrent / twelveHoursInMs) * chartWidth.value).dp
                                    val currentXPos = ((currentTimeFromCurrent / twelveHoursInMs) * chartWidth.value).dp

                                    val prevDurationSeconds = (prevEvent.stop - prevEvent.createAt) / 1000
                                    val currentDurationSeconds = (currentEvent.stop - currentEvent.createAt) / 1000

                                    val prevYPos = ((maxY - prevDurationSeconds).toFloat() / (maxY - minY) * chartHeight.value).dp
                                    val currentYPos = ((maxY - currentDurationSeconds).toFloat() / (maxY - minY) * chartHeight.value).dp

                                    // 선의 중점에 간격 텍스트 표시
                                    val midX = (prevXPos + currentXPos) / 2
                                    val midY = (prevYPos + currentYPos) / 2

                                    // 진통 간격 계산 (분 단위)
                                    val intervalMinutes = (currentEvent.createAt - prevEvent.stop) / 1000 / 60

                                    Box(
                                        modifier = Modifier
                                            .offset(x = midX, y = midY - 12.dp)
                                            .background(
                                                Color(0xFFFF6B9D).copy(alpha = 0.15f),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "${intervalMinutes}분",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFF6B9D)
                                        )
                                    }
                                }
                            }

                            // 데이터 점들
                            val twelveHoursInMs = 12 * 60 * 60 * 1000f

                            filteredEvents.forEachIndexed { index, event ->
                                // x축: 현재 시간을 기준점으로 한 상대적 위치 (과거일수록 오른쪽)
                                val timeFromCurrent = (currentTime - event.createAt).toFloat()
                                val xPosition = ((timeFromCurrent / twelveHoursInMs) * chartWidth.value).dp

                                // y축: 지속시간 계산 (초 단위)
                                val durationSeconds = (event.stop - event.createAt) / 1000
                                val yPosition = ((maxY - durationSeconds).toFloat() / (maxY - minY) * chartHeight.value).dp

                                // 점이 차트 범위 내에 있는지 확인
                                if (xPosition >= 0.dp && xPosition <= chartWidth) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .offset(x = xPosition - 8.dp, y = yPosition - 8.dp)
                                            .clickable { onPointSelected(event) }
                                            .clip(CircleShape)
                                    ) {
                                        // 선택된 점은 더 크게 표시
                                        val pointSize = if (selectedPoint == event) 10.dp else 8.dp
                                        val pointColor = if (selectedPoint == event) Color(0xFFFF1744) else Color(0xFFFF6B9D)

                                        Box(
                                            modifier = Modifier
                                                .size(pointSize)
                                                .align(Alignment.Center)
                                                .clip(CircleShape)
                                                .background(pointColor)
                                        )

                                        // 선택된 점 주변에 효과 추가
                                        if (selectedPoint == event) {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .align(Alignment.Center)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFFF1744).copy(alpha = 0.2f))
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 지속시간 라벨 - y축 옆에 세로로 배치
                        Text(
                            text = "지속 시간 (초)",
                            fontSize = 10.sp,
                            color = MainColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = -25.dp)
                                .offset(y = 100.dp)
                                .rotate(-90f)
                        )

                        // x축 라벨 - 차트 하단에 배치
                        Text(
                            text = "시간 간격 (2시간)",
                            fontSize = 10.sp,
                            color = MainColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(x = -200.dp)
                        )
                    }
                }
            }
        }

        // 선택된 점의 정보 표시 (차트 밖으로 이동)
        selectedPoint?.let { point ->
            val durationSeconds = (point.stop - point.createAt) / 1000
            val sortedEvents = filteredEvents.sortedBy { it.createAt }
            val currentIndex = sortedEvents.indexOf(point)

            // 이전 점과의 간격 계산
            val intervalText = if (currentIndex > 0) {
                val previousEvent = sortedEvents[currentIndex - 1]
                val intervalMinutes = (point.createAt - previousEvent.stop) / 1000 / 60
                "${intervalMinutes}분 간격"
            } else {
                "첫 번째 진통"
            }

            // 발생 시간 표시 (몇 시간 전인지)
            val hoursAgo = (currentTime - point.createAt) / 1000 / 60 / 60
            val timeText = when {
                hoursAgo < 1 -> "${((currentTime - point.createAt) / 1000 / 60).toInt()}분 전"
                hoursAgo < 24 -> "${hoursAgo.toInt()}시간 전"
                else -> "${(hoursAgo / 24).toInt()}일 전"
            }

            // 선택된 점 정보를 차트 아래에 표시
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "진통 정보",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MainColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "발생 시간",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = timeText,
                            fontSize = 12.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "지속 시간",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "${durationSeconds}초",
                            fontSize = 12.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "간격",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = intervalText,
                            fontSize = 12.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 스크롤 힌트 텍스트 (데이터가 있을 때만 표시)
        if (filteredEvents.isNotEmpty()) {
            Text(
                text = "← 좌우로 스크롤하여 전체 그래프를 확인하세요",
                fontSize = 11.sp,
                color = Color.Gray,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

//@Composable
//fun ContractionScatterChart(
//    events: List<ContractionEvent>,
//    selectedPoint: ContractionEvent?,
//    onPointSelected: (ContractionEvent?) -> Unit
//) {
//    val maxX = 12 // x축 최대값 (12시간)
//    val minY = 0 // y축 최소값 (0초)
//    val maxY = 120 // y축 최대값 (120초 = 2분)
//    val chartWidth = 320.dp // 차트 폭 약간 증가
//    val chartHeight = 240.dp // 차트 높이 약간 증가
//
//    Box(
//        modifier = Modifier
//            .fillMaxWidth()
//            .height(chartHeight + 80.dp)
//            .background(Color.White),
//        contentAlignment = Alignment.Center
//    ) {
//        Box(
//            modifier = Modifier.size(chartWidth + 80.dp, chartHeight + 80.dp)
//        ) {
//            // y축 눈금 (세로) - 0초부터 120초까지 20초 단위
//            Column(
//                modifier = Modifier
//                    .height(chartHeight)
//                    .offset(x = 50.dp, y = 20.dp),
//                verticalArrangement = Arrangement.SpaceBetween
//            ) {
//                for (i in maxY downTo minY step 20) {
//                    Text(
//                        text = "${i}초",
//                        fontSize = 11.sp,
//                        color = Color.Black,
//                        fontWeight = FontWeight.Medium,
//                        textAlign = TextAlign.End,
//                        maxLines = 1,
//                        modifier = Modifier
//                            .offset(x = (-40).dp)
//                            .width(35.dp)
//                    )
//                }
//            }
//
//            // x축 눈금 (가로) - 12시간 전부터 현재까지 2시간 단위로 표시
//            Row(
//                modifier = Modifier
//                    .width(chartWidth)
//                    .align(Alignment.BottomStart)
//                    .offset(x = 50.dp, y = (-20).dp),
//                horizontalArrangement = Arrangement.SpaceBetween
//            ) {
//                for (i in 0..maxX step 2) {
//                    Text(
//                        text = "${maxX - i}시간전", // 12시간전, 10시간전, ... 현재
//                        fontSize = 10.sp,
//                        color = Color.Black,
//                        fontWeight = FontWeight.Medium
//                    )
//                }
//            }
//
//            // 격자 영역
//            Box(
//                modifier = Modifier
//                    .size(chartWidth, chartHeight)
//                    .offset(x = 35.dp, y = 10.dp)
//                    .background(Color(0xFFFAFAFA))
//            ) {
//                // 세로선 (10분 단위)
//                for (i in 0..maxX step 10) {
//                    Box(
//                        modifier = Modifier
//                            .width(1.dp)
//                            .fillMaxHeight()
//                            .background(Color(0xFFE0E0E0))
//                            .offset(x = (i * chartWidth.value / maxX).dp)
//                    )
//                }
//
//                // 가로선 (20초 단위)
//                for (i in 0..((maxY - minY) / 20)) {
//                    Box(
//                        modifier = Modifier
//                            .height(1.dp)
//                            .fillMaxWidth()
//                            .background(Color(0xFFE0E0E0))
//                            .offset(y = (i * chartHeight.value / ((maxY - minY) / 20)).dp)
//                    )
//                }
//
//                // 점들을 이어주는 선 그리기
//                if (events.size > 1) {
//                    val sortedEvents = events.sortedBy { it.createAt }
//                    val startTime = events.minOf { it.createAt }
//
//                    Canvas(modifier = Modifier.fillMaxSize()) {
//                        val path = Path()
//
//                        sortedEvents.forEachIndexed { index, event ->
//                            val timeFromStart = (event.createAt - startTime) / 1000 / 60
//                            val xPos = timeFromStart.toFloat() / maxX * size.width
//                            val durationSeconds = (event.stop - event.createAt) / 1000
//                            val yPos =
//                                (maxY - durationSeconds).toFloat() / (maxY - minY) * size.height
//
//                            if (index == 0) {
//                                path.moveTo(xPos, yPos)
//                            } else {
//                                path.lineTo(xPos, yPos)
//                            }
//                        }
//
//                        drawPath(
//                            path = path,
//                            color = Color(0xFFFF6B9D),
//                            style = Stroke(width = 2.dp.toPx())
//                        )
//                    }
//                }
//
//                // 선 위에 진통 간격 텍스트 표시
//                if (events.size > 1) {
//                    val sortedEvents = events.sortedBy { it.createAt }
//                    val startTime = events.minOf { it.createAt }
//
//                    for (i in 1 until sortedEvents.size) {
//                        val prevEvent = sortedEvents[i - 1]
//                        val currentEvent = sortedEvents[i]
//
//                        // 이전 점과 현재 점의 위치 계산
//                        val prevTimeFromStart = (prevEvent.createAt - startTime) / 1000 / 60
//                        val prevXPos = (prevTimeFromStart.toFloat() / maxX * chartWidth.value).dp
//                        val prevDurationSeconds = (prevEvent.stop - prevEvent.createAt) / 1000
//                        val prevYPos =
//                            ((maxY - prevDurationSeconds).toFloat() / (maxY - minY) * chartHeight.value).dp
//
//                        val currentTimeFromStart = (currentEvent.createAt - startTime) / 1000 / 60
//                        val currentXPos =
//                            (currentTimeFromStart.toFloat() / maxX * chartWidth.value).dp
//                        val currentDurationSeconds =
//                            (currentEvent.stop - currentEvent.createAt) / 1000
//                        val currentYPos =
//                            ((maxY - currentDurationSeconds).toFloat() / (maxY - minY) * chartHeight.value).dp
//
//                        // 선의 중점에 간격 텍스트 표시
//                        val midX = (prevXPos + currentXPos) / 2
//                        val midY = (prevYPos + currentYPos) / 2
//
//                        // 진통 간격 계산 (분 단위)
//                        val intervalMinutes = (currentEvent.createAt - prevEvent.stop) / 1000 / 60
//
//                        Box(
//                            modifier = Modifier
//                                .offset(x = midX, y = midY - 10.dp)
//                                .background(
//                                    MainColor.copy(alpha = 0.2f),
//                                    RoundedCornerShape(4.dp)
//                                )
//                                .padding(horizontal = 4.dp, vertical = 2.dp)
//                        ) {
//                            Text(
//                                text = "${intervalMinutes}분",
//                                fontSize = 10.sp,
//                                fontWeight = FontWeight.Medium,
//                                color = Color.Black
//                            )
//                        }
//                    }
//                }
//
//                // 데이터 점들
//                events.forEachIndexed { index, event ->
//                    // x축: 한시간 동안의 절대 시간 위치 계산
//                    val startTime = events.minOf { it.createAt }
//                    val timeFromStart = (event.createAt - startTime) / 1000 / 60 // 분 단위
//                    val xPosition = (timeFromStart.toFloat() / maxX * chartWidth.value).dp
//
//                    // y축: 지속시간 계산 (초 단위)
//                    val durationSeconds = (event.stop - event.createAt) / 1000
//                    val yPosition =
//                        ((maxY - durationSeconds).toFloat() / (maxY - minY) * chartHeight.value).dp
//
//                    Box(
//                        modifier = Modifier
//                            .size(12.dp) // 클릭 영역을 위해 크기 증가
//                            .offset(x = xPosition - 6.dp, y = yPosition - 6.dp)
//                            .clickable { onPointSelected(event) }
//                            .clip(CircleShape)
//                    ) {
//                        Box(
//                            modifier = Modifier
//                                .size(6.dp)
//                                .align(Alignment.Center)
//                                .clip(CircleShape)
//                                .background(
//                                    if (selectedPoint == event) Color(0xFFFF1744)
//                                    else Color(0xFFFF6B9D)
//                                )
//                        )
//                    }
//                }
//            }
//
//            // 지속시간 라벨 - y축 밑에 세로로 배치
//            Text(
//                text = "지속 시간 (초)",
//                fontSize = 10.sp,
//                color = Color(0xFFFF1744),
//                fontWeight = FontWeight.Bold,
//                modifier = Modifier
//                    .align(Alignment.CenterStart)
//                    .offset(x = -35.dp)
//                    .offset(y = 50.dp)
//                    .rotate(-90f)
//            )
//
//            // 간격시간 라벨 - x축 밑에 배치
//            Text(
//                text = "간격 시간 (분)",
//                fontSize = 10.sp,
//                color = Color(0xFFFF1744),
//                fontWeight = FontWeight.Bold,
//                modifier = Modifier
//                    .align(Alignment.BottomCenter)
//                    .offset(y = 0.dp)
//                    .offset(x = -105.dp)
//            )
//        }
//
//        // 선택된 점의 정보 표시
//        selectedPoint?.let { point ->
//            val durationSeconds = (point.stop - point.createAt) / 1000
//            val sortedEvents = events.sortedBy { it.createAt }
//            val currentIndex = sortedEvents.indexOf(point)
//
//            // 이전 점과의 간격 계산
//            val intervalText = if (currentIndex > 0) {
//                val previousEvent = sortedEvents[currentIndex - 1]
//                val intervalMinutes = (point.createAt - previousEvent.stop) / 1000 / 60
//                "${intervalMinutes}분 간격"
//            } else {
//                "첫 번째 진통"
//            }
//
//            Card(
//                modifier = Modifier
//                    .align(Alignment.TopStart)
//                    .offset(x = 20.dp, y = 10.dp),
//                colors = CardDefaults.cardColors(containerColor = Color.White),
//                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
//                shape = RoundedCornerShape(8.dp)
//            ) {
//                Column(
//                    modifier = Modifier.padding(8.dp),
//                    horizontalAlignment = Alignment.Start
//                ) {
//                    Text(
//                        text = "진통 정보",
//                        fontSize = 11.sp,
//                        fontWeight = FontWeight.Bold,
//                        color = Color.Black
//                    )
//                    Spacer(modifier = Modifier.height(3.dp))
//                    Text(
//                        text = "지속시간: ${durationSeconds}초",
//                        fontSize = 10.sp,
//                        color = Color.Black
//                    )
//                    Text(
//                        text = intervalText,
//                        fontSize = 10.sp,
//                        color = Color.Gray
//                    )
//                }
//            }
//        }
//    }
//}

@Preview(showBackground = true)
@Composable
fun RecordDetailScreenPreview() {
    RecordDetailScreen(navController = null as NavHostController)
}