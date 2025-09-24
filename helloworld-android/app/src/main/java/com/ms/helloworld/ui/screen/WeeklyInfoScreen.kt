package com.ms.helloworld.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ms.helloworld.viewmodel.WeeklyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyInfoScreen(
    initialWeek: Int = 1,
    onBackClick: () -> Unit,
    viewModel: WeeklyViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(initialWeek) {
        viewModel.loadWeeklyData(initialWeek)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        // 상단 헤더
        WeeklyInfoHeader(
            currentWeek = state.currentWeek,
            onBackClick = onBackClick,
            onPreviousWeek = { viewModel.changeWeek(state.currentWeek - 1) },
            onNextWeek = { viewModel.changeWeek(state.currentWeek + 1) }
        )

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // 주차별 정보 제목
                Text(
                    text = "${state.currentWeek}주차 정보",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "임신 ${state.currentWeek}주차에 알아두면 좋은 정보들을 정리했어요",
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // 메인 정보 카드
                state.weeklyInfo?.let { info ->
                    MainInfoCard(
                        weekNo = state.currentWeek,
                        info = info
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 추가 정보 섹션들
                AdditionalInfoSections(weekNo = state.currentWeek)

                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }

    // 에러 처리
    state.errorMessage?.let { error ->
        LaunchedEffect(error) {
            viewModel.clearError()
        }
    }
}

@Composable
private fun WeeklyInfoHeader(
    currentWeek: Int,
    onBackClick: () -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "뒤로가기",
                tint = Color(0xFF333333)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPreviousWeek,
                enabled = currentWeek > 1
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowLeft,
                    contentDescription = "이전 주차",
                    tint = if (currentWeek > 1) Color(0xFF333333) else Color(0xFFCCCCCC)
                )
            }

            Text(
                text = "${currentWeek}주차",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333),
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            IconButton(
                onClick = onNextWeek,
                enabled = currentWeek < 42
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "다음 주차",
                    tint = if (currentWeek < 42) Color(0xFF333333) else Color(0xFFCCCCCC)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MainInfoCard(
    weekNo: Int,
    info: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    text = "📖",
                    fontSize = 32.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )

                Column {
                    Text(
                        text = "이번 주 핵심 정보",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF666666)
                    )
                    Text(
                        text = "${weekNo}주차",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                }
            }

            Divider(color = Color(0xFFE0E0E0))

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = info,
                fontSize = 16.sp,
                color = Color(0xFF333333),
                lineHeight = 24.sp,
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
private fun AdditionalInfoSections(weekNo: Int) {
    val sections = getAdditionalInfoSections(weekNo)

    sections.forEach { section ->
        InfoSectionCard(
            title = section.title,
            icon = section.icon,
            content = section.content,
            backgroundColor = section.backgroundColor
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun InfoSectionCard(
    title: String,
    icon: String,
    content: String,
    backgroundColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text(
                    text = icon,
                    fontSize = 24.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
            }

            Text(
                text = content,
                fontSize = 14.sp,
                color = Color(0xFF666666),
                lineHeight = 20.sp
            )
        }
    }
}

data class InfoSection(
    val title: String,
    val icon: String,
    val content: String,
    val backgroundColor: Color
)

private fun getAdditionalInfoSections(weekNo: Int): List<InfoSection> {
    return when (weekNo) {
        in 1..4 -> listOf(
            InfoSection(
                title = "태아 발달",
                icon = "👶",
                content = "수정란이 착상하고 기본적인 신체 구조가 형성되기 시작합니다. 엽산 섭취가 중요한 시기예요.",
                backgroundColor = Color(0xFFE8F5E8)
            ),
            InfoSection(
                title = "몸의 변화",
                icon = "🤰",
                content = "입덧이 시작될 수 있고, 피로감을 많이 느낄 수 있어요. 충분한 휴식을 취하세요.",
                backgroundColor = Color(0xFFFFF3E0)
            ),
            InfoSection(
                title = "주의사항",
                icon = "⚠️",
                content = "술, 담배, 카페인을 피하고, 의사와 상담하여 필요한 영양제를 복용하세요.",
                backgroundColor = Color(0xFFFFEBEE)
            )
        )

        in 5..12 -> listOf(
            InfoSection(
                title = "태아 발달",
                icon = "👶",
                content = "주요 장기들이 형성되고, 심장박동을 확인할 수 있어요. 태아의 기본적인 외형이 갖춰집니다.",
                backgroundColor = Color(0xFFE8F5E8)
            ),
            InfoSection(
                title = "몸의 변화",
                icon = "🤰",
                content = "입덧이 심해질 수 있고, 가슴이 부드러워지며 커질 수 있어요. 소변이 자주 마려울 수 있습니다.",
                backgroundColor = Color(0xFFFFF3E0)
            ),
            InfoSection(
                title = "검사 항목",
                icon = "🏥",
                content = "첫 산전검사를 받고, 기본 혈액검사와 소변검사를 진행하세요.",
                backgroundColor = Color(0xFFE1F5FE)
            )
        )

        in 13..20 -> listOf(
            InfoSection(
                title = "태아 발달",
                icon = "👶",
                content = "성별 확인이 가능하고, 태동을 느낄 수 있어요. 태아의 뼈가 단단해지기 시작합니다.",
                backgroundColor = Color(0xFFE8F5E8)
            ),
            InfoSection(
                title = "몸의 변화",
                icon = "🤰",
                content = "입덧이 줄어들고 식욕이 돌아와요. 배가 조금씩 나오기 시작합니다.",
                backgroundColor = Color(0xFFFFF3E0)
            ),
            InfoSection(
                title = "필요한 준비",
                icon = "📝",
                content = "임신복 구입을 고려하고, 태교를 시작해보세요. 정기 산전검사를 꾸준히 받으세요.",
                backgroundColor = Color(0xFFF3E5F5)
            )
        )

        in 21..28 -> listOf(
            InfoSection(
                title = "태아 발달",
                icon = "👶",
                content = "태아의 움직임이 활발해지고, 청각이 발달하여 소리에 반응할 수 있어요.",
                backgroundColor = Color(0xFFE8F5E8)
            ),
            InfoSection(
                title = "몸의 변화",
                icon = "🤰",
                content = "배가 점점 커지고, 허리 통증이 생길 수 있어요. 체중 관리에 신경 써야 합니다.",
                backgroundColor = Color(0xFFFFF3E0)
            ),
            InfoSection(
                title = "중요한 검사",
                icon = "🏥",
                content = "임신성 당뇨 검사와 빈혈 검사를 받으세요. 정기적인 태아 성장 확인이 중요해요.",
                backgroundColor = Color(0xFFE1F5FE)
            )
        )

        in 29..36 -> listOf(
            InfoSection(
                title = "태아 발달",
                icon = "👶",
                content = "태아의 폐가 성숙하고, 대부분의 신체 기능이 완성됩니다. 체중이 빠르게 증가해요.",
                backgroundColor = Color(0xFFE8F5E8)
            ),
            InfoSection(
                title = "몸의 변화",
                icon = "🤰",
                content = "숨이 가빠지고, 소화불량이 생길 수 있어요. 불면증을 겪을 수 있습니다.",
                backgroundColor = Color(0xFFFFF3E0)
            ),
            InfoSection(
                title = "출산 준비",
                icon = "🍼",
                content = "출산용품을 준비하고, 병원 가방을 미리 싸두세요. 출산 교육에 참여해보세요.",
                backgroundColor = Color(0xFFF3E5F5)
            )
        )

        else -> listOf(
            InfoSection(
                title = "태아 발달",
                icon = "👶",
                content = "태아가 완전히 성숙하여 언제든 출산이 가능한 상태예요. 머리가 아래로 향합니다.",
                backgroundColor = Color(0xFFE8F5E8)
            ),
            InfoSection(
                title = "몸의 변화",
                icon = "🤰",
                content = "배가 아래로 내려오고, 진통이 시작될 수 있어요. 출산 징후를 잘 관찰하세요.",
                backgroundColor = Color(0xFFFFF3E0)
            ),
            InfoSection(
                title = "최종 준비",
                icon = "🏥",
                content = "언제든 병원에 갈 수 있도록 준비하고, 진통 간격을 체크하세요. 연락처를 확인해두세요.",
                backgroundColor = Color(0xFFFFEBEE)
            )
        )
    }
}