package com.ms.helloworld.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ms.helloworld.dto.response.ContractionSession
import com.ms.helloworld.dto.response.FetalMovementRecord
import com.ms.helloworld.dto.response.StressLevel
import com.ms.helloworld.dto.response.WearableResponse
import com.ms.helloworld.repository.WearRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "싸피_WearableViewModel"
@HiltViewModel
class WearableViewModel @Inject constructor(
    private val wearRepository: WearRepository
): ViewModel() {

    // 기존 코드는 그대로 두고 추가
    private val _wearableData = MutableStateFlow<WearableResponse?>(null)
    val wearableData: StateFlow<WearableResponse?> = _wearableData.asStateFlow()

    private val _isLoadingWearable = MutableStateFlow(false)
    val isLoadingWearable: StateFlow<Boolean> = _isLoadingWearable.asStateFlow()

    private val _contractions = MutableStateFlow<List<ContractionSession>>(emptyList())
    val contractions: StateFlow<List<ContractionSession>> = _contractions.asStateFlow()

    // 이번주 태동 기록 상태
    private val _fetalMovements = MutableStateFlow<List<FetalMovementRecord>>(emptyList())
    val fetalMovements: StateFlow<List<FetalMovementRecord>> = _fetalMovements.asStateFlow()

    // 지난주 태동 기록 상태 추가
    private val _previousWeekFetalMovements = MutableStateFlow<List<FetalMovementRecord>>(emptyList())
    val previousWeekFetalMovements: StateFlow<List<FetalMovementRecord>> = _previousWeekFetalMovements.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingFetal = MutableStateFlow(false)
    val isLoadingFetal: StateFlow<Boolean> = _isLoadingFetal.asStateFlow()

    // 지난주 태동 로딩 상태 추가
    private val _isLoadingPreviousWeekFetal = MutableStateFlow(false)
    val isLoadingPreviousWeekFetal: StateFlow<Boolean> = _isLoadingPreviousWeekFetal.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // 스트레스 레벨 상태 추가
    private val _stressLevel = MutableStateFlow(StressLevel.STABLE)
    val stressLevel: StateFlow<StressLevel> = _stressLevel.asStateFlow()

    // 웨어러블 데이터 로드 메서드 추가
    fun loadWearableData() {
        viewModelScope.launch {
            _isLoadingWearable.value = true
            _error.value = null

            wearRepository.getLatestData()
                .onSuccess { response ->
                    _wearableData.value = response
                    // 스트레스 레벨 자동 계산
                    val stressScore = response.heartrate?.stress ?: 50
                    _stressLevel.value = StressLevel.fromScore(stressScore)

                    val heartRate = response.heartrate?.hr ?: 0
                    val stress = response.heartrate?.stress ?: 0
                    val steps = response.step?.steps ?: 0
                    Log.d(TAG, "웨어러블 데이터 로드 성공: 심박수=${heartRate}, 스트레스=${stress}, 걸음수=${steps}")
                    Log.d(TAG, "계산된 스트레스 레벨: ${_stressLevel.value.displayName}")
                }
                .onFailure { throwable ->
                    Log.e(TAG, "웨어러블 데이터 로드 실패", throwable)
                    _error.value = throwable.message ?: "웨어러블 데이터를 불러오는데 실패했습니다."
                }

            _isLoadingWearable.value = false
        }
    }

    fun loadContractions(from: String? = null, to: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            wearRepository.getContractions(from, to)
                .onSuccess { response ->
                    _contractions.value = response.sessions
                }
                .onFailure { throwable ->
                    _error.value = throwable.message ?: "알 수 없는 오류가 발생했습니다."
                }

            _isLoading.value = false
        }
    }

    // 이번주 태동 기록 로드 메소드
    fun loadFetalMovements(from: String? = null, to: String? = null) {

        viewModelScope.launch {
            _isLoadingFetal.value = true
            _error.value = null

            wearRepository.getFetalMovement(from, to)
                .onSuccess { response ->
                    _fetalMovements.value = response.records
                }
                .onFailure { throwable ->
                    Log.e(TAG, "이번주 태동 데이터 로드 실패", throwable)
                    _error.value = throwable.message ?: "태동 기록을 불러오는데 실패했습니다."
                    Log.e(TAG, "에러 메시지 설정: ${_error.value}")
                }

            _isLoadingFetal.value = false
            Log.d(TAG, "태동 로딩 상태를 false로 설정")
            Log.d(TAG, "=== loadFetalMovements 종료 (이번주) ===")
        }
    }

    // 지난주 태동 기록 로드 메소드 추가
    fun loadPreviousWeekFetalMovements(from: String? = null, to: String? = null) {
        viewModelScope.launch {
            _isLoadingPreviousWeekFetal.value = true
            _error.value = null

            wearRepository.getFetalMovement(from, to)
                .onSuccess { response ->
                    _previousWeekFetalMovements.value = response.records
                }
                .onFailure { throwable ->
                    _error.value = throwable.message ?: "지난주 태동 기록을 불러오는데 실패했습니다."
                }

            _isLoadingPreviousWeekFetal.value = false
        }
    }


    fun clearError() {
        _error.value = null
    }
}