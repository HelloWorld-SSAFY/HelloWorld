package com.ms.helloworld.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.airbnb.lottie.compose.*
import com.ms.helloworld.R
import com.ms.helloworld.navigation.Screen
import com.ms.helloworld.ui.theme.BaseColor
import com.ms.helloworld.ui.theme.MainColor
import com.ms.helloworld.viewmodel.LoginViewModel

@Composable
fun LoginScreen(
    navController: NavHostController,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    // 수동 로그인 성공 시 화면 이동
    LaunchedEffect(state.loginSuccess) {
        if (state.loginSuccess) {
            viewModel.handleLoginSuccess(navController)
        }
    }

    // 에러 메시지 표시
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BaseColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Lottie 애니메이션을 Box로 감싸서 절대 위치 지정
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.splashlottie))

                var isPlaying by remember { mutableStateOf(true) }

                val progress by animateLottieCompositionAsState(
                    composition = composition,
                    isPlaying = isPlaying,
                    iterations = LottieConstants.IterateForever,
                    speed = 0.25f,
                    clipSpec = LottieClipSpec.Frame(min = 0, max = 10),
                    cancellationBehavior = LottieCancellationBehavior.OnIterationFinish
                )

                LottieAnimation(
                    composition = composition,
                    progress = { progress },
                    modifier = Modifier
                        .size(400.dp)
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Google 로그인 버튼
                OutlinedButton(
                    onClick = {
                        viewModel.signInWithGoogle(context)
                    },
                    enabled = !state.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        disabledContainerColor = Color.White,
                        disabledContentColor = Color.Black.copy(alpha = 0.38f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color.Black.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_google_logo),
                            contentDescription = "Google Logo",
                            tint = Color.Unspecified,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Google로 로그인",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }

                }
            }

            // 하단 여백
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    LoginScreen(navController = null as NavHostController)
}