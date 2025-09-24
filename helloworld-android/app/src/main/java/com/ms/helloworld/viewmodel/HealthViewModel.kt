package com.ms.helloworld.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ms.helloworld.dto.request.MaternalHealthCreateRequest
import com.ms.helloworld.dto.request.MaternalHealthUpdateRequest
import com.ms.helloworld.dto.response.MaternalHealthGetResponse
import com.ms.helloworld.dto.response.MaternalHealthItem
import com.ms.helloworld.repository.MaternalHealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

private const val TAG = "HealthViewModel"

data class HealthState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val todayHealthData: MaternalHealthGetResponse? = null,
    val healthHistory: List<MaternalHealthItem> = emptyList(),
    val editingData: MaternalHealthItem? = null,
    val isEditMode: Boolean = false
)

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val maternalHealthRepository: MaternalHealthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HealthState())
    val state: StateFlow<HealthState> = _state.asStateFlow()

    init {
        loadTodayHealthData()
    }

    fun loadTodayHealthData() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val result = maternalHealthRepository.getTodayMaternalHealth()
                if (result.isSuccess) {
                    val healthData = result.getOrNull()
                    _state.value = _state.value.copy(
                        isLoading = false,
                        todayHealthData = healthData
                    )
                    Log.d(TAG, "✅ 오늘 건강 데이터 로딩 완료: $healthData")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "건강 데이터 로딩 실패"
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = error
                    )
                    Log.e(TAG, "❌ 오늘 건강 데이터 로딩 실패: $error")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "네트워크 오류"
                )
                Log.e(TAG, "💥 예외 발생: ${e.message}", e)
                e.printStackTrace()
            }
        }
    }

    fun loadHealthHistory(from: String? = null, to: String? = null) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val result = maternalHealthRepository.getMaternalHealthList(from, to)
                if (result.isSuccess) {
                    val historyData = result.getOrNull()?.records ?: emptyList()
                    _state.value = _state.value.copy(
                        isLoading = false,
                        healthHistory = historyData
                    )
                    Log.d(TAG, "✅ 건강 히스토리 로딩 완료: ${historyData.size}개")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "건강 히스토리 로딩 실패"
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = error
                    )
                    Log.e(TAG, "❌ 건강 히스토리 로딩 실패: $error")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "네트워크 오류"
                )
                Log.e(TAG, "💥 히스토리 로딩 예외 발생: ${e.message}", e)
                e.printStackTrace()
            }
        }
    }

    fun createHealthRecord(
        weight: BigDecimal,
        maxBloodPressure: Int,
        minBloodPressure: Int,
        bloodSugar: Int,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val request = MaternalHealthCreateRequest(
                    weight = weight,
                    maxBloodPressure = maxBloodPressure,
                    minBloodPressure = minBloodPressure,
                    bloodSugar = bloodSugar
                )

                val result = maternalHealthRepository.createMaternalHealth(request)
                if (result.isSuccess) {
                    Log.d(TAG, "✅ 건강 데이터 생성 성공")
                    _state.value = _state.value.copy(isLoading = false)
                    // 성공 시 오늘 데이터 다시 로드
                    loadTodayHealthData()
                    // 성공 콜백 실행
                    onSuccess()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "건강 데이터 생성 실패"
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = error
                    )
                    Log.e(TAG, "❌ 건강 데이터 생성 실패: $error")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "네트워크 오류"
                )
                Log.e(TAG, "💥 생성 예외 발생: ${e.message}", e)
                e.printStackTrace()
            }
        }
    }

    fun updateHealthRecord(
        maternalId: Long,
        weight: BigDecimal? = null,
        bloodPressure: String? = null,
        bloodSugar: Int? = null
    ) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val request = MaternalHealthUpdateRequest(
                    weight = weight,
                    bloodPressure = bloodPressure,
                    bloodSugar = bloodSugar
                )

                val result = maternalHealthRepository.updateMaternalHealth(maternalId, request)
                if (result.isSuccess) {
                    Log.d(TAG, "✅ 건강 데이터 수정 성공")
                    // 성공 시 오늘 데이터 다시 로드
                    loadTodayHealthData()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "건강 데이터 수정 실패"
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = error
                    )
                    Log.e(TAG, "❌ 건강 데이터 수정 실패: $error")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "네트워크 오류"
                )
                Log.e(TAG, "💥 수정 예외 발생: ${e.message}", e)
                e.printStackTrace()
            }
        }
    }

    fun deleteHealthRecord(maternalId: Long) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val result = maternalHealthRepository.deleteMaternalHealth(maternalId)
                if (result.isSuccess) {
                    Log.d(TAG, "✅ 건강 데이터 삭제 성공")
                    // 성공 시 오늘 데이터 다시 로드
                    loadTodayHealthData()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "건강 데이터 삭제 실패"
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = error
                    )
                    Log.e(TAG, "❌ 건강 데이터 삭제 실패: $error")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "네트워크 오류"
                )
                Log.e(TAG, "💥 삭제 예외 발생: ${e.message}", e)
                e.printStackTrace()
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    // 혈압 파싱 헬퍼 함수
    fun parseBloodPressure(bloodPressure: String): Pair<Int, Int>? {
        return try {
            val parts = bloodPressure.split("/")
            if (parts.size == 2) {
                val systolic = parts[0].toInt()
                val diastolic = parts[1].toInt()
                Pair(systolic, diastolic)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // 혈압 포맷 헬퍼 함수
    fun formatBloodPressure(systolic: Int, diastolic: Int): String {
        return "$systolic/$diastolic"
    }

    // 수정용 데이터 설정
    fun setEditingData(data: MaternalHealthItem) {
        _state.value = _state.value.copy(
            editingData = data,
            isEditMode = true
        )
        Log.d(TAG, "📝 수정용 데이터 설정: ID=${data.maternalId}, 체중=${data.weight}, 혈압=${data.bloodPressure}, 혈당=${data.bloodSugar}")
    }

    // 수정 모드 초기화
    fun clearEditingData() {
        _state.value = _state.value.copy(
            editingData = null,
            isEditMode = false
        )
        Log.d(TAG, "🧹 수정 모드 초기화")
    }

    // HealthData를 MaternalHealthItem으로 변환하여 수정용 데이터 설정
    fun setEditingDataFromHealthData(healthData: com.ms.helloworld.ui.screen.HealthData) {
        try {
            // HealthData를 MaternalHealthItem으로 변환
            val maternalHealthItem = MaternalHealthItem(
                maternalId = 0L, // HealthData에는 ID가 없으므로 0으로 설정 (실제 수정 시 다른 방법으로 ID를 찾아야 함)
                recordDate = healthData.recordDate ?: "",
                weight = java.math.BigDecimal(healthData.weight?.toDouble() ?: 0.0),
                bloodPressure = "${healthData.bloodPressureHigh?.toInt() ?: 0}/${healthData.bloodPressureLow?.toInt() ?: 0}",
                bloodSugar = healthData.bloodSugar?.toInt() ?: 0,
                createdAt = ""
            )

            _state.value = _state.value.copy(
                editingData = maternalHealthItem,
                isEditMode = true
            )
            Log.d(TAG, "📝 HealthData에서 수정용 데이터 설정: 체중=${maternalHealthItem.weight}, 혈압=${maternalHealthItem.bloodPressure}, 혈당=${maternalHealthItem.bloodSugar}")
        } catch (e: Exception) {
            Log.e(TAG, "HealthData 변환 실패: ${e.message}", e)
        }
    }
}