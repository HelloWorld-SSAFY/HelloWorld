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
    BASIC_INFO_FORM,
    MOM_INFO_FORM,
    DAD_INFO_FORM
}

private const val TAG = "싸피_OnboardingScreen"
@Composable
fun OnboardingScreens(
    navController: NavHostController,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    val baseScreens = listOf(
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
            title = "기본 정보를 입력해주세요",
            screenType = ScreenType.BASIC_INFO_FORM
        )
    )

    // 성별에 따라 추가 화면 동적 생성
    val screens = remember(state.selectedGender, state.nickname, state.age) {
        buildList {
            addAll(baseScreens)

            // 성별이 선택되었고, 기본 정보가 입력된 경우에만 추가 화면 표시
            if (state.selectedGender.isNotBlank() &&
                state.nickname.isNotBlank() &&
                state.age.isNotBlank()) {

                when (state.selectedGender) {
                    "엄마" -> add(OnboardingScreen(
                        title = "임신 관련 정보를 입력해주세요",
                        screenType = ScreenType.MOM_INFO_FORM
                    ))
                    "아빠" -> add(OnboardingScreen(
                        title = "초대 코드를 입력해주세요",
                        screenType = ScreenType.DAD_INFO_FORM
                    ))
                }
            }
        }
    }

    val pagerState = rememberPagerState(pageCount = { screens.size })
    val scope = rememberCoroutineScope()

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

    // 기본 정보 입력 완료 시 pager 상태 업데이트
    LaunchedEffect(screens.size) {
        // screens 크기가 변경되면 pagerState도 업데이트됨
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
            val currentScreen = screens[pagerState.currentPage]

            val isButtonEnabled = when {
                state.isLoading -> false // 로딩 중이면 비활성화
                currentScreen.screenType == ScreenType.BASIC_INFO_FORM -> {
                    // 기본 정보 입력 화면: 성별, 태명, 나이가 모두 입력되어야 함
                    state.selectedGender.isNotBlank() &&
                    state.nickname.isNotBlank() &&
                    state.age.isNotBlank()
                }
                currentScreen.screenType == ScreenType.MOM_INFO_FORM -> {
                    // 엄마 상세 정보 화면: 임신 관련 정보가 모두 입력되어야 함
                    state.isChildbirth != null &&
                    state.menstrualDate.isNotBlank() &&
                    state.menstrualCycle.isNotBlank()
                }
                currentScreen.screenType == ScreenType.DAD_INFO_FORM -> {
                    // 아빠 상세 정보 화면: 초대코드가 검증되어야 함
                    state.invitationCode.isNotBlank() &&
                    state.isInviteCodeValid
                }
                else -> true // 다른 화면들은 항상 활성화
            }


            // 다음 버튼 - 하단에 고정
            Button(
                onClick = {
                    scope.launch {
                        if (isLastPage) {
                            // 마지막 페이지: 최종 완료 처리
                            viewModel.completeOnboarding()
                        } else if (currentScreen.screenType == ScreenType.BASIC_INFO_FORM) {
                            // Basic 화면에서 다음: Member 정보 저장
                            val success = viewModel.saveBasicInfo()
                            if (success) {
                                pagerState.animateScrollToPage(
                                    pagerState.currentPage + 1,
                                    animationSpec = tween(durationMillis = 500)
                                )
                            }
                        } else {
                            // 일반 다음 페이지 이동
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
                        state.isLoading -> "처리 중..."
                        currentScreen.screenType == ScreenType.BASIC_INFO_FORM -> "다음"
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
            fontSize = if (screen.screenType == ScreenType.BASIC_INFO_FORM ||
                           screen.screenType == ScreenType.MOM_INFO_FORM ||
                           screen.screenType == ScreenType.DAD_INFO_FORM) 20.sp else 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(
                    bottom = 8.dp,
                    top = if (screen.screenType == ScreenType.BASIC_INFO_FORM ||
                            screen.screenType == ScreenType.MOM_INFO_FORM ||
                            screen.screenType == ScreenType.DAD_INFO_FORM) 16.dp else 0.dp
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
            ScreenType.BASIC_INFO_FORM -> {
                if (viewModel != null) {
                    BasicInfoFormContent(
                        viewModel = viewModel,
                        state = onboardingState,
                        modifier = Modifier.padding(bottom = 100.dp)
                    )
                }
            }
            ScreenType.MOM_INFO_FORM -> {
                if (viewModel != null) {
                    MomInfoFormContent(
                        viewModel = viewModel,
                        state = onboardingState,
                        modifier = Modifier.padding(bottom = 100.dp)
                    )
                }
            }
            ScreenType.DAD_INFO_FORM -> {
                if (viewModel != null) {
                    DadInfoFormContent(
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
fun BasicInfoFormContent(
    viewModel: OnboardingViewModel,
    state: OnboardingState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // 성별
            Column {
                Text(
                    text = "성별",
                    fontSize = 16.sp,
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

        // 성별 선택 후에만 나머지 필드들 표시
        item {
            AnimatedVisibility(
                visible = state.selectedGender.isNotBlank(),
                enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
    }
}

@Composable
fun MomInfoFormContent(
    viewModel: OnboardingViewModel,
    state: OnboardingState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // 출산 경험 여부
            Column {
                Text(
                    text = "출산 경험 여부",
                    fontSize = 16.sp,
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
        }

        item {
            // 마지막 생리일
            FormFieldUpdated(
                label = "마지막 생리일",
                value = state.menstrualDate,
                onValueChange = { viewModel.updateMenstrualDate(it) },
                isDateField = true
            )
        }

        item {
            // 생리 주기
            FormFieldUpdated(
                label = "생리 주기(일)",
                value = state.menstrualCycle,
                onValueChange = { viewModel.updateMenstrualCycle(it) }
            )
        }

        // 계산된 임신 주차 표시
        item {
            AnimatedVisibility(
                visible = state.calculatedPregnancyWeek > 0,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                Column {
                    Text(
                        text = "계산된 임신 주차",
                        fontSize = 16.sp,
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

@Composable
fun DadInfoFormContent(
    viewModel: OnboardingViewModel,
    state: OnboardingState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // 초대 코드
            FormFieldUpdated(
                label = "초대 코드",
                value = state.invitationCode,
                onValueChange = { viewModel.updateInvitationCode(it) }
            )
        }

        // 검증 버튼
        item {
            if (state.invitationCode.isNotBlank()) {
                Button(
                    onClick = { viewModel.validateInviteCode() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isValidatingInviteCode && !state.isInviteCodeValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isInviteCodeValid) Color(0xFF4CAF50) else MainColor,
                        disabledContainerColor = Color(0xFFE0E0E0)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    when {
                        state.isValidatingInviteCode -> Text("검증 중...", color = Color.White)
                        state.isInviteCodeValid -> Text("✓ 검증 완료", color = Color.White)
                        else -> Text("초대 코드 검증", color = Color.White)
                    }
                }
            }
        }

        // 에러 메시지
        item {
            state.inviteCodeError?.let { error ->
                Text(
                    text = error,
                    color = Color(0xFFE53E3E),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
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