package com.ms.helloworld.ui.screen

import android.annotation.SuppressLint
import android.util.Log
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ms.helloworld.viewmodel.HealthViewModel
import java.math.BigDecimal

@SuppressLint("NewApi")
@Composable
fun HealthRegisterScreen(
    navController: NavHostController,
    viewModel: HealthViewModel = hiltViewModel()
) {
    // 이전 화면(HealthStatusScreen)과 같은 ViewModel 인스턴스를 사용하기 위해
    // 이전 backStackEntry의 ViewModel을 가져옴
    val parentEntry = remember(navController) {
        navController.getBackStackEntry("health_status")
    }
    val sharedViewModel: HealthViewModel = hiltViewModel(parentEntry)
    val backgroundColor = Color(0xFFF5F5F5)
    val state by sharedViewModel.state.collectAsState()

    // ViewModel에서 수정용 데이터 읽기
    val editingData = state.editingData
    val isEditMode = state.isEditMode

    // 수정용 데이터가 있으면 혈압을 파싱
    val (initialSystolic, initialDiastolic) = editingData?.let { data ->
        sharedViewModel.parseBloodPressure(data.bloodPressure) ?: (0 to 0)
    } ?: (0 to 0)

    Log.d("HealthRegisterScreen", "ViewModel에서 데이터 읽기:")
    Log.d("HealthRegisterScreen", "isEditMode: $isEditMode")
    Log.d("HealthRegisterScreen", "editingData: $editingData")
    if (editingData != null) {
        Log.d("HealthRegisterScreen", "weight: ${editingData.weight}")
        Log.d("HealthRegisterScreen", "bloodPressure: ${editingData.bloodPressure}")
        Log.d("HealthRegisterScreen", "bloodSugar: ${editingData.bloodSugar}")
        Log.d("HealthRegisterScreen", "parsed BP: $initialSystolic/$initialDiastolic")
    }

    // 입력 상태들 - ViewModel 데이터로 초기화
    var weight by remember { mutableStateOf(editingData?.weight?.toString() ?: "") }
    var systolicBP by remember { mutableStateOf(if (initialSystolic > 0) initialSystolic.toString() else "") }
    var diastolicBP by remember { mutableStateOf(if (initialDiastolic > 0) initialDiastolic.toString() else "") }
    var bloodSugar by remember { mutableStateOf(if ((editingData?.bloodSugar ?: 0) > 0) editingData?.bloodSugar.toString() else "") }

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

            // 등록/수정 버튼
            HealthRegisterButton(
                text = if (isEditMode) "수정" else "등록",
                onClick = {
                    if (isEditMode && editingData != null) {
                        // 수정 모드
                        updateHealthData(
                            viewModel = sharedViewModel,
                            maternalId = editingData.maternalId,
                            weight = weight,
                            systolicBP = systolicBP,
                            diastolicBP = diastolicBP,
                            bloodSugar = bloodSugar,
                            onSuccess = {
                                sharedViewModel.clearEditingData()
                                navController.popBackStack()
                            }
                        )
                    } else {
                        // 등록 모드
                        saveHealthData(
                            viewModel = sharedViewModel,
                            weight = weight,
                            systolicBP = systolicBP,
                            diastolicBP = diastolicBP,
                            bloodSugar = bloodSugar,
                            onSuccess = {
                                sharedViewModel.clearEditingData()
                                navController.popBackStack()
                            }
                        )
                    }
                },
                enabled = isFormValid(weight, systolicBP, diastolicBP, bloodSugar) && !state.isLoading
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
fun HealthRegisterButton(
    text: String = "등록",
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
            text = text,
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

private const val TAG = "HealthRegisterScreen"

fun saveHealthData(
    viewModel: HealthViewModel,
    weight: String,
    systolicBP: String,
    diastolicBP: String,
    bloodSugar: String,
    onSuccess: () -> Unit = {}
) {
    Log.d(TAG, "건강 데이터 저장 시작")

    try {
        // 입력값 변환 및 기본값 설정
        val weightValue = if (weight.isNotBlank()) {
            BigDecimal(weight)
        } else {
            BigDecimal.ZERO
        }

        val systolicValue = if (systolicBP.isNotBlank()) {
            systolicBP.toInt()
        } else {
            0
        }

        val diastolicValue = if (diastolicBP.isNotBlank()) {
            diastolicBP.toInt()
        } else {
            0
        }

        val bloodSugarValue = if (bloodSugar.isNotBlank()) {
            bloodSugar.toInt()
        } else {
            0
        }

        Log.d(TAG, "변환된 데이터: 체중=$weightValue, 혈압=$systolicValue/$diastolicValue, 혈당=$bloodSugarValue")

        // ViewModel을 통해 서버에 데이터 저장
        viewModel.createHealthRecord(
            weight = weightValue,
            maxBloodPressure = systolicValue,
            minBloodPressure = diastolicValue,
            bloodSugar = bloodSugarValue,
            onSuccess = onSuccess
        )

        Log.d(TAG, "건강 데이터 저장 요청 완료")

    } catch (e: Exception) {
        Log.e(TAG, "건강 데이터 저장 실패: ${e.message}", e)
    }
}

fun updateHealthData(
    viewModel: HealthViewModel,
    maternalId: Long,
    weight: String,
    systolicBP: String,
    diastolicBP: String,
    bloodSugar: String,
    onSuccess: () -> Unit = {}
) {
    Log.d(TAG, "건강 데이터 수정 시작 - ID: $maternalId")

    try {
        // 입력값 변환 (빈 값은 null로 처리)
        val weightValue = if (weight.isNotBlank()) {
            BigDecimal(weight)
        } else {
            null
        }

        val bloodPressureValue = if (systolicBP.isNotBlank() && diastolicBP.isNotBlank()) {
            "${systolicBP}/${diastolicBP}"
        } else {
            null
        }

        val bloodSugarValue = if (bloodSugar.isNotBlank()) {
            bloodSugar.toInt()
        } else {
            null
        }

        Log.d(TAG, "변환된 수정 데이터: 체중=$weightValue, 혈압=$bloodPressureValue, 혈당=$bloodSugarValue")

        // ViewModel을 통해 서버에 데이터 수정
        viewModel.updateHealthRecord(
            maternalId = maternalId,
            weight = weightValue,
            bloodPressure = bloodPressureValue,
            bloodSugar = bloodSugarValue
        )

        Log.d(TAG, "건강 데이터 수정 요청 완료")
        onSuccess()

    } catch (e: Exception) {
        Log.e(TAG, "건강 데이터 수정 실패: ${e.message}", e)
    }
}

@Preview(showBackground = true)
@Composable
fun HealthRegisterScreenPreview() {
    HealthRegisterScreen(navController = null as NavHostController)
}