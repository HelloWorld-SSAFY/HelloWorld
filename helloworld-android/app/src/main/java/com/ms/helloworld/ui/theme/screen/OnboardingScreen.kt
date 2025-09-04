package com.ms.helloworld.ui.theme.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ms.helloworld.R
import kotlinx.coroutines.launch

data class OnboardingScreen(
    val title: String,
    val subtitle: String = "",
    val description: String = "",
    val screenType: ScreenType = ScreenType.TEXT_ONLY,
    val backgroundColor: Color = Color(0xFFFAEDBA)
)

enum class ScreenType {
    TEXT_ONLY,
    WITH_ICONS,
    WITH_WEARABLE,
    WITH_FAMILY,
    WITH_NIGHT_SCENE,
    USER_INFO_FORM
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreens(
    onFinish: () -> Unit = {}
) {
    val screens = listOf(
        OnboardingScreen(
            title = "반가워요!",
            subtitle = "회원님",
            description = "소중한 임신 여정을 함께할 준비가 되었어요",
            screenType = ScreenType.TEXT_ONLY
        ),
        OnboardingScreen(
            title = "주차별 생활 가이드와 체크리스트로",
            subtitle = "매일 안심할 수 있어요",
            screenType = ScreenType.WITH_ICONS
        ),
        OnboardingScreen(
            title = "웨어러블로 측정한 마음·몸",
            subtitle = "지표를 관리해요",
            screenType = ScreenType.WITH_WEARABLE
        ),
        OnboardingScreen(
            title = "배우자와 일정을 공유하고",
            subtitle = "함께 기록할 수 있어요",
            screenType = ScreenType.WITH_FAMILY
        ),
        OnboardingScreen(
            title = "당신과 아기를 위한 여정,",
            subtitle = "지금 시작해요",
            screenType = ScreenType.WITH_NIGHT_SCENE
        ),
        OnboardingScreen(
            title = "더 나은 맞춤 케어를 위해\n 몇 가지 정보를 입력주세요",
            screenType = ScreenType.USER_INFO_FORM
        )
    )

    val pagerState = rememberPagerState(pageCount = { screens.size })
    val scope = rememberCoroutineScope()

    // 애니메이션을 위한 알파 값
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(600),
        label = "content_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screens[pagerState.currentPage].backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // HorizontalPager로 화면들을 가로로 스와이프 가능하게 배치
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                OnboardingPage(
                    screen = screens[page],
                    alpha = alpha
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // 진행 표시기 - 버튼 바로 위에 배치
            ProgressIndicator(
                currentStep = pagerState.currentPage,
                totalSteps = screens.size,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // 다음 버튼 - 하단에 고정
            Button(
                onClick = {
                    scope.launch {
                        if (pagerState.currentPage < screens.size - 1) {
                            pagerState.animateScrollToPage(
                                pagerState.currentPage + 1,
                                animationSpec = tween(durationMillis = 500)
                            )
                        } else {
                            onFinish()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 56.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0C7B33)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (pagerState.currentPage == screens.size - 1) "시작하기" else "다음",
                    fontSize = 16.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun OnboardingPage(
    screen: OnboardingScreen,
    alpha: Float
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = screen.title,
            fontSize = if (screen.screenType == ScreenType.USER_INFO_FORM) 20.sp else 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0C7B33),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(bottom = 8.dp, top = if (screen.screenType == ScreenType.USER_INFO_FORM) 16.dp else 0.dp)
        )

        if (screen.subtitle.isNotEmpty()) {
            Text(
                text = screen.subtitle,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0C7B33),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        if (screen.description.isNotEmpty()) {
            Text(
                text = screen.description,
                fontSize = 16.sp,
                color = Color(0xFF666666),
                textAlign = TextAlign.Center,
            )
        }

        // 화면 타입별 콘텐츠
        when (screen.screenType) {
            ScreenType.WITH_ICONS -> IconsContent()
            ScreenType.WITH_WEARABLE -> WearableContent()
            ScreenType.WITH_FAMILY -> FamilyContent()
            ScreenType.WITH_NIGHT_SCENE -> NightSceneContent()
            ScreenType.USER_INFO_FORM -> UserInfoFormContent(onFormComplete = {}, modifier = Modifier.padding(bottom = 100.dp))
            else -> Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun UserInfoFormContent(
    onFormComplete: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var nickname by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf("아빠") } // 아빠가 선택된 상태
    var age by remember { mutableStateOf("") }
    var isFirstPregnancy by remember { mutableStateOf<Boolean?>(true) } // 첫아이가 선택된 상태
    var pregnancyCount by remember { mutableStateOf("") }
    var lastMenstrualDate by remember { mutableStateOf("") }
    var menstrualCycle by remember { mutableStateOf("") }

    // 스크롤 가능한 카드 형태의 폼
    Card(
        modifier = Modifier
            .fillMaxSize()
            .heightIn(max = 500.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                // 태명
                FormFieldUpdated(
                    label = "태명",
                    value = nickname,
                    onValueChange = { nickname = it }
                )
            }

            item {
                // 성별
                Column {
                    Text(
                        text = "성별",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SelectionButton(
                            text = "엄마",
                            isSelected = selectedGender == "엄마",
                            onClick = { selectedGender = "엄마" }
                        )
                        SelectionButton(
                            text = "아빠",
                            isSelected = selectedGender == "아빠",
                            onClick = { selectedGender = "아빠" }
                        )
                    }
                }
            }

            item {
                // 나이
                FormFieldUpdated(
                    label = "나이",
                    value = age,
                    onValueChange = { age = it }
                )
            }

            item {
                // 출산 경험 여부
                Column {
                    Text(
                        text = "출산 경험 여부",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SelectionButton(
                            text = "있어요",
                            isSelected = isFirstPregnancy == true,
                            onClick = { isFirstPregnancy = true }
                        )
                        SelectionButton(
                            text = "없어요",
                            isSelected = isFirstPregnancy == false,
                            onClick = { isFirstPregnancy = false }
                        )
                    }
                }
            }

            item {
                // 출산 경험 횟수
                FormFieldUpdated(
                    label = "출산 경험 횟수",
                    value = pregnancyCount,
                    onValueChange = { pregnancyCount = it }
                )
            }

            item {
                // 마지막 생리일
                FormFieldUpdated(
                    label = "마지막 생리일",
                    value = lastMenstrualDate,
                    onValueChange = { lastMenstrualDate = it },
                    isDateField = true
                )
            }

            item {
                // 생리주기(일)
                FormFieldUpdated(
                    label = "생리주기(일)",
                    value = menstrualCycle,
                    onValueChange = { menstrualCycle = it }
                )
            }
        }
    }
}

@Composable
fun FormFieldUpdated(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isDateField: Boolean = false
) {
    Column {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF2E7D32),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    Color(0xFFF8F8F8),
                    RoundedCornerShape(8.dp)
                )
                .border(
                    1.dp,
                    Color(0xFFE0E0E0),
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = Color.Black
                    ),
                    modifier = Modifier.weight(1f)
                )

                if (isDateField) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "날짜 선택",
                        tint = Color(0xFF9E9E9E),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SelectionButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clickable { onClick() }
            .background(
                if (isSelected) Color(0xFF2E7D32) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                if (isSelected) Color(0xFF2E7D32) else Color(0xFFE0E0E0),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isSelected) Color.White else Color(0xFF757575)
        )
    }
}

@Composable
fun ProgressIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(totalSteps) { index ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == currentStep) Color(0xFF0C7B33)
                        else Color(0xFFE0E0E0)
                    )
            )
            if (index < totalSteps - 1) {
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

@Composable
fun IconsContent() {
    Row(
        modifier = Modifier.padding(vertical = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        IconCard(
            icon = painterResource(id = R.drawable.ic_check_list),
            backgroundColor = Color(0xFFFFD4D4),
        )
        IconCard(
            icon = painterResource(id = R.drawable.ic_calendar),
            backgroundColor = Color(0xFFA3D7FF),
        )
        IconCard(
            icon = painterResource(id = R.drawable.ic_baby),
            backgroundColor = Color(0xFFC1F59A),
        )
    }
}

@Composable
fun WearableContent() {
    Row(
        modifier = Modifier.padding(vertical = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        IconCard(
            icon = painterResource(id = R.drawable.ic_watch),
            backgroundColor = Color(0xFFD8DFFF),
        )
        IconCard(
            icon = painterResource(id = R.drawable.ic_heart),
            backgroundColor = Color(0xFFFFD9F7),
        )
    }
}

@Composable
fun FamilyContent() {
    Box(
        modifier = Modifier
            .size(300.dp, 240.dp)
            .clip(RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_family),
                contentDescription = null,
                modifier = Modifier.size(300.dp)
            )
        }
    }
}

@Composable
fun NightSceneContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 중앙 콘텐츠
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_mom),
                contentDescription = null,
                modifier = Modifier.size(350.dp)
            )
        }
    }
}

@Composable
fun IconCard(
    icon: Painter,
    backgroundColor: Color,
) {
    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = Color.Unspecified
        )
    }
}

@Preview(showBackground = true)
@Composable
fun OnboardingScreensPreview() {
    MaterialTheme {
        OnboardingScreens()
    }
}