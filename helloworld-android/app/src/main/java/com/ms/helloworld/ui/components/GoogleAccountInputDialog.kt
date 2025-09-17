package com.ms.helloworld.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun GoogleAccountInputDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    isLoading: Boolean = false
) {
    if (isVisible) {
        var email by remember { mutableStateOf("") }
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current

        // 이메일 유효성 검사
        val isValidEmail = remember(email) {
            email.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
        }

        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 제목
                    Text(
                        text = "Google 계정으로 로그인",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 설명
                    Text(
                        text = "개발용 테스트를 위해 Google 계정을 입력해주세요",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 이메일 입력 필드
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Google 계정 (이메일)") },
                        placeholder = { Text("example@gmail.com") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                if (isValidEmail && !isLoading) {
                                    onConfirm(email)
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        singleLine = true,
                        isError = email.isNotBlank() && !isValidEmail,
                        supportingText = {
                            if (email.isNotBlank() && !isValidEmail) {
                                Text(
                                    text = "올바른 이메일 형식을 입력해주세요",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 버튼들
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 취소 버튼
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading
                        ) {
                            Text("취소")
                        }

                        // 확인 버튼
                        Button(
                            onClick = { onConfirm(email) },
                            modifier = Modifier.weight(1f),
                            enabled = isValidEmail && !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isValidEmail) Color(0xFF4285F4) else Color.Gray
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "로그인",
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // 다이얼로그가 열릴 때 포커스 요청
        LaunchedEffect(isVisible) {
            if (isVisible) {
                focusRequester.requestFocus()
            }
        }
    }
}