package com.ms.helloworld.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

@SuppressLint("NewApi")
@Composable
fun HealthRegisterScreen(
    navController: NavHostController
) {
    val backgroundColor = Color(0xFFF5F5F5)

    // 입력 상태들
    var weight by remember { mutableStateOf("") }
    var systolicBP by remember { mutableStateOf("") }
    var diastolicBP by remember { mutableStateOf("") }
    var bloodSugar by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 뒤로가기 버튼만 있는 TopAppBar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
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
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 체중 입력
            InputSection(
                label = "체중",
                value = weight,
                onValueChange = { weight = it },
                placeholder = "kg",
                keyboardType = KeyboardType.Decimal
            )

            // 혈압 입력 (수축기/이완기)
            BloodPressureInputSection(
                systolicValue = systolicBP,
                diastolicValue = diastolicBP,
                onSystolicChange = { systolicBP = it },
                onDiastolicChange = { diastolicBP = it }
            )

            // 혈당 입력
            InputSection(
                label = "혈당",
                value = bloodSugar,
                onValueChange = { bloodSugar = it },
                placeholder = "mg/dL",
                keyboardType = KeyboardType.Number
            )

            Spacer(modifier = Modifier.weight(1f))

            // 등록 버튼
            RegisterButton(
                onClick = {
                    // 데이터 저장 로직
                    saveHealthData(
                        weight = weight.toFloatOrNull(),
                        systolicBP = systolicBP.toFloatOrNull(),
                        diastolicBP = diastolicBP.toFloatOrNull(),
                        bloodSugar = bloodSugar.toFloatOrNull()
                    )
                    navController.popBackStack()
                },
                enabled = isFormValid(weight, systolicBP, diastolicBP, bloodSugar)
            )
        }
    }
}

@Composable
fun InputSection(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFF49699),
                unfocusedBorderColor = Color(0xFFE0E0E0),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            ),
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true
        )
    }
}

@Composable
fun BloodPressureInputSection(
    systolicValue: String,
    diastolicValue: String,
    onSystolicChange: (String) -> Unit,
    onDiastolicChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "혈압",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 수축기 혈압
            OutlinedTextField(
                value = systolicValue,
                onValueChange = onSystolicChange,
                placeholder = {
                    Text(
                        text = "수축기",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFF49699),
                    unfocusedBorderColor = Color(0xFFE0E0E0),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            Text(
                text = "/",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray
            )

            // 이완기 혈압
            OutlinedTextField(
                value = diastolicValue,
                onValueChange = onDiastolicChange,
                placeholder = {
                    Text(
                        text = "이완기",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFF49699),
                    unfocusedBorderColor = Color(0xFFE0E0E0),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            Text(
                text = "mmHg",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
fun RegisterButton(
    onClick: () -> Unit,
    enabled: Boolean
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFF49699),
            disabledContainerColor = Color(0xFFE0E0E0)
        )
    ) {
        Text(
            text = "등록",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) Color.White else Color.Gray
        )
    }
}

// 유틸리티 함수들
fun isFormValid(
    weight: String,
    systolicBP: String,
    diastolicBP: String,
    bloodSugar: String
): Boolean {
    // 최소 하나의 필드는 입력되어야 함
    return weight.isNotBlank() ||
            (systolicBP.isNotBlank() && diastolicBP.isNotBlank()) ||
            bloodSugar.isNotBlank()
}

fun saveHealthData(
    weight: Float?,
    systolicBP: Float?,
    diastolicBP: Float?,
    bloodSugar: Float?
) {
    // 실제 구현에서는 Repository나 ViewModel을 통해 데이터 저장
    println("Saving health data:")
    weight?.let { println("Weight: $it kg") }
    if (systolicBP != null && diastolicBP != null) {
        println("Blood Pressure: $systolicBP/$diastolicBP mmHg")
    }
    bloodSugar?.let { println("Blood Sugar: $it mg/dL") }
}

@Preview(showBackground = true)
@Composable
fun HealthRegisterScreenPreview() {
    HealthRegisterScreen(navController = null as NavHostController)
}