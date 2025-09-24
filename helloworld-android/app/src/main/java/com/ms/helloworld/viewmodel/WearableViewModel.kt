package com.ms.helloworld.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ms.helloworld.dto.response.ContractionSession
import com.ms.helloworld.dto.response.FetalMovementRecord
import com.ms.helloworld.repository.WearRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WearableViewModel @Inject constructor(
    private val wearRepository: WearRepository
): ViewModel() {

    private val _contractions = MutableStateFlow<List<ContractionSession>>(emptyList())
    val contractions: StateFlow<List<ContractionSession>> = _contractions.asStateFlow()

    // 태동 기록 상태 추가
    private val _fetalMovements = MutableStateFlow<List<FetalMovementRecord>>(emptyList())
    val fetalMovements: StateFlow<List<FetalMovementRecord>> = _fetalMovements.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingFetal = MutableStateFlow(false)
    val isLoadingFetal: StateFlow<Boolean> = _isLoadingFetal.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

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

    // 태동 기록 로드 메소드 추가
    fun loadFetalMovements(from: String? = null, to: String? = null) {
        viewModelScope.launch {
            _isLoadingFetal.value = true
            _error.value = null

            wearRepository.getFetalMovement(from, to)
                .onSuccess { response ->
                    _fetalMovements.value = response.records
                }
                .onFailure { throwable ->
                    _error.value = throwable.message ?: "태동 기록을 불러오는데 실패했습니다."
                }

            _isLoadingFetal.value = false
        }
    }


    fun clearError() {
        _error.value = null
    }
}