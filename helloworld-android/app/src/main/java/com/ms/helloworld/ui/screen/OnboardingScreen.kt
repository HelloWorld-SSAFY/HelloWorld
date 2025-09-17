package com.ms.helloworld.ui.screen

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ms.helloworld.R
import com.ms.helloworld.navigation.Screen
import com.ms.helloworld.ui.theme.MainColor
import com.ms.helloworld.viewmodel.OnboardingState
import com.ms.helloworld.viewmodel.OnboardingViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class OnboardingScreen(
    val title: String,
    val subtitle: String = "",
    val description: String = "",
    val screenType: ScreenType = ScreenType.TEXT_ONLY
)

enum class ScreenType {
    TEXT_ONLY,
    WITH_ICONS,
    WITH_WEARABLE,
    WITH_FAMILY,
    WITH_NIGHT_SCENE,
    USER_INFO_FORM
}

private const val TAG = "싸피_OnboardingScreen"
@Composable
fun OnboardingScreens(
    navController: NavHostController,
    viewModel: OnboardingViewModel = hiltViewModel()
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
    val state by viewModel.state.collectAsState()

    // 애니메이션을 위한 알파 값
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(600),
        label = "content_alpha"
    )

    // API 호출 성공 시 홈 화면으로 이동
    LaunchedEffect(state.submitSuccess) {
        if (state.submitSuccess) {
            navController.navigate(Screen.HomeScreen.route) {
                popUpTo(Screen.OnboardingScreens.route) { inclusive = true }
            }
        }
    }

    // 에러 메시지 표시
    state.errorMessage?.let { error ->
        LaunchedEffect(error) {
            viewModel.clearError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
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
                    alpha = alpha,
                    viewModel = viewModel,
                    onboardingState = state
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // 진행 표시기 - 버튼 바로 위에 배치
            ProgressIndicator(
                currentStep = pagerState.currentPage,
                totalSteps = screens.size,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // 다음/시작하기 버튼
            val isLastPage = pagerState.currentPage == screens.size - 1
            val isButtonEnabled = if (isLastPage) {
                // 로딩 중이면 비활성화
                !state.isLoading && when (state.selectedGender) {
                    "엄마" -> {
                        state.nickname.isNotBlank() &&
                        state.age.isNotBlank() &&
                        state.selectedGender.isNotBlank() &&
                        state.isChildbirth != null &&
                        state.menstrualDate.isNotBlank() &&
                        state.menstrualCycle.isNotBlank()
                    }
                    "아빠" -> {
                        state.nickname.isNotBlank() &&
                        state.age.isNotBlank() &&
                        state.selectedGender.isNotBlank() &&
                        state.invitationCode.isNotBlank()
                    }
                    else -> false
                }
            } else {
                true // 마지막 페이지가 아니면 항상 활성화
            }


            // 다음 버튼 - 하단에 고정
            Button(
                onClick = {
                    scope.launch {
                        if (isLastPage) {
                            // 회원 정보 업데이트 API 호출
                            viewModel.submitUserInfo()
                        } else {
                            pagerState.animateScrollToPage(
                                pagerState.currentPage + 1,
                                animationSpec = tween(durationMillis = 500)
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 56.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isButtonEnabled) MainColor else Color(0xFFD0D0D0),
                    disabledContainerColor = Color(0xFFD0D0D0)
                ),
                shape = RoundedCornerShape(16.dp),
                enabled = isButtonEnabled
            ) {
                Text(
                    text = when {
                        isLastPage && state.isLoading -> "처리 중..."
                        isLastPage -> "시작하기"
                        else -> "다음"
                    },
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
    alpha: Float,
    viewModel: OnboardingViewModel? = null,
    onboardingState: OnboardingState = OnboardingState()
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
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(
                    bottom = 8.dp,
                    top = if (screen.screenType == ScreenType.USER_INFO_FORM) 16.dp else 0.dp
                )
        )

        if (screen.subtitle.isNotEmpty()) {
            Text(
                text = screen.subtitle,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
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
            ScreenType.USER_INFO_FORM -> {
                if (viewModel != null) {
                    Log.d(TAG, "OnboardingPage: ")
                    UserInfoFormContent(
                        viewModel = viewModel,
                        state = onboardingState,
                        modifier = Modifier.padding(bottom = 100.dp)
                    )
                }
            }

            else -> Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun UserInfoFormContent(
    viewModel: OnboardingViewModel,
    state: OnboardingState,
    modifier: Modifier = Modifier
) {

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            // 성별 - 첫 번째로 변경
            Column {
                Text(
                    text = "성별",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SelectionButton(
                        text = "엄마",
                        isSelected = state.selectedGender == "엄마",
                        onClick = { viewModel.updateGender("엄마") }
                    )
                    SelectionButton(
                        text = "아빠",
                        isSelected = state.selectedGender == "아빠",
                        onClick = { viewModel.updateGender("아빠") }
                    )
                }
            }
        }

        // 성별 선택 후에만 나머지 필드들 표시 (애니메이션 적용)
        item {
            AnimatedVisibility(
                visible = state.selectedGender.isNotBlank(),
                enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 태명
                    FormFieldUpdated(
                        label = "태명",
                        value = state.nickname,
                        onValueChange = { viewModel.updateNickname(it) }
                    )

                    // 닉네임 미리보기
                    if (state.nickname.isNotBlank() && state.selectedGender.isNotBlank()) {
                        NicknamePreview(
                            nickname = state.nickname,
                            gender = state.selectedGender
                        )
                    }

                    // 나이
                    FormFieldUpdated(
                        label = "나이",
                        value = state.age,
                        onValueChange = { viewModel.updateAge(it) }
                    )
                }
            }
        }

        // 엄마만 보이는 필드들 (애니메이션 적용)
        item {
            AnimatedVisibility(
                visible = state.selectedGender == "엄마",
                enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 출산 경험 여부
                    Column {
                        Text(
                            text = "출산 경험 여부",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SelectionButton(
                                text = "있어요",
                                isSelected = state.isChildbirth == true,
                                onClick = { viewModel.updateChildbirthStatus(true) }
                            )
                            SelectionButton(
                                text = "없어요",
                                isSelected = state.isChildbirth == false,
                                onClick = { viewModel.updateChildbirthStatus(false) }
                            )
                        }
                    }

                    // 마지막 생리일
                    FormFieldUpdated(
                        label = "마지막 생리일",
                        value = state.menstrualDate,
                        onValueChange = { viewModel.updateMenstrualDate(it) },
                        isDateField = true
                    )

                    // 생리 주기
                    FormFieldUpdated(
                        label = "생리 주기(일)",
                        value = state.menstrualCycle,
                        onValueChange = { viewModel.updateMenstrualCycle(it) }
                    )

                    // 계산된 임신 주차 표시
                    AnimatedVisibility(
                        visible = state.calculatedPregnancyWeek > 0,
                        enter = fadeIn(animationSpec = tween(300)),
                        exit = fadeOut(animationSpec = tween(300))
                    ) {
                        Column {
                            Text(
                                text = "계산된 임신 주차",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .background(
                                        Color(0xFFF5F5F5),
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
                                Text(
                                    text = "${state.calculatedPregnancyWeek}주",
                                    fontSize = 14.sp,
                                    color = MainColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // 아빠만 보이는 필드들 (애니메이션 적용)
        item {
            AnimatedVisibility(
                visible = state.selectedGender == "아빠",
                enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))
            ) {
                // 초대 코드
                FormFieldUpdated(
                    label = "초대 코드",
                    value = state.invitationCode,
                    onValueChange = { viewModel.updateInvitationCode(it) }
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
    var isFocused by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // 플레이스홀더 텍스트 정의
    val placeholder = when (label) {
        "태명" -> "태명을 입력해주세요"
        "나이" -> "나이를 입력해주세요"
        "마지막 생리일" -> "연도-월-일"
        "임신 주차" -> "주차를 입력해주세요"
        "생리주기(일)" -> "일수를 입력해주세요"
        "초대 코드" -> "초대 코드를 입력해주세요"
        else -> ""
    }

    Column {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    Color(0xFFFFFFFF), // 배경색 변경
                    RoundedCornerShape(8.dp)
                )
                .border(
                    1.dp,
                    if (isFocused) Color(0xFFF49699) else Color(0xFFD0D0D0), // 외곽선 색상 변경
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
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    // 플레이스홀더 텍스트
                    if (value.isEmpty() && !isFocused) {
                        Text(
                            text = placeholder,
                            fontSize = 14.sp,
                            color = Color(0xFF808080)
                        )
                    }

                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = Color.Black
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusEvent { focusState ->
                                isFocused = focusState.isFocused
                            },
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Black),
                        singleLine = true
                    )
                }

                if (isDateField) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "날짜 선택",
                        tint = Color(0xFF9E9E9E),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { showDatePicker = true }
                    )
                }
            }
        }
    }

    // 날짜 선택 다이얼로그
    if (showDatePicker) {
        StepwiseDatePickerDialog(
            onDateSelected = { dateString ->
                onValueChange(dateString)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
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
                if (isSelected) MainColor else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                if (isSelected) MainColor else Color(0xFFE0E0E0),
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
                        if (index == currentStep) MainColor
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

@Composable
fun NicknamePreview(
    nickname: String,
    gender: String
) {
    val combinedNickname = "${nickname} ${gender}"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color(0xFFF0F9FF), // 연한 파란색 배경
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                MainColor.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = "등록될 닉네임",
                fontSize = 12.sp,
                color = Color(0xFF666666),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = combinedNickname,
                fontSize = 14.sp,
                color = MainColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StepwiseDatePickerDialog(
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(1) } // 1: 연도, 2: 월, 3: 일
    var selectedYear by remember { mutableStateOf(LocalDate.now().year) }
    var selectedMonth by remember { mutableStateOf(LocalDate.now().monthValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (step) {
                    1 -> "연도 선택"
                    2 -> "월 선택"
                    else -> "일 선택"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            when (step) {
                1 -> YearSelection(
                    selectedYear = selectedYear,
                    onYearSelected = { year ->
                        selectedYear = year
                        step = 2
                    }
                )
                2 -> MonthSelection(
                    selectedMonth = selectedMonth,
                    onMonthSelected = { month ->
                        selectedMonth = month
                        step = 3
                    }
                )
                3 -> DaySelection(
                    year = selectedYear,
                    month = selectedMonth,
                    onDaySelected = { day ->
                        val dateString = String.format("%04d-%02d-%02d", selectedYear, selectedMonth, day)
                        onDateSelected(dateString)
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            if (step > 1) {
                TextButton(
                    onClick = { step-- }
                ) {
                    Text("이전")
                }
            }
        }
    )
}

@Composable
fun YearSelection(
    selectedYear: Int,
    onYearSelected: (Int) -> Unit
) {
    val currentYear = LocalDate.now().year
    val years = (currentYear - 10..currentYear).toList()

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.height(300.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(years) { year ->
            Box(
                modifier = Modifier
                    .height(50.dp)
                    .clickable { onYearSelected(year) }
                    .background(
                        if (year == selectedYear) MainColor else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        1.dp,
                        if (year == selectedYear) MainColor else Color(0xFFE0E0E0),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = year.toString(),
                    fontSize = 14.sp,
                    color = if (year == selectedYear) Color.White else Color.Black,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun MonthSelection(
    selectedMonth: Int,
    onMonthSelected: (Int) -> Unit
) {
    val months = listOf(
        "1월", "2월", "3월", "4월", "5월", "6월",
        "7월", "8월", "9월", "10월", "11월", "12월"
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.height(300.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(months.size) { index ->
            val month = index + 1
            Box(
                modifier = Modifier
                    .height(50.dp)
                    .clickable { onMonthSelected(month) }
                    .background(
                        if (month == selectedMonth) MainColor else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        1.dp,
                        if (month == selectedMonth) MainColor else Color(0xFFE0E0E0),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = months[index],
                    fontSize = 14.sp,
                    color = if (month == selectedMonth) Color.White else Color.Black,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun DaySelection(
    year: Int,
    month: Int,
    onDaySelected: (Int) -> Unit
) {
    val yearMonth = YearMonth.of(year, month)
    val daysInMonth = yearMonth.lengthOfMonth()
    val days = (1..daysInMonth).toList()

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.height(300.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(days) { day ->
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clickable { onDaySelected(day) }
                    .background(
                        Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        1.dp,
                        Color(0xFFE0E0E0),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = day.toString(),
                    fontSize = 12.sp,
                    color = Color.Black,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun OnboardingScreensPreview() {
    MaterialTheme {
        OnboardingScreens(navController = null as NavHostController)
    }
}