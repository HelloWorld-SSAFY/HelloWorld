package com.ms.helloworld.viewmodel

import com.ms.helloworld.dto.response.OutingDelivery
import com.ms.helloworld.repository.OutingRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OutingUiState(
    val isLoading: Boolean = false,
    val outings: List<OutingDelivery> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class OutingViewModel @Inject constructor(
    private val repository: OutingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OutingUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadOutingRecommendations()
    }

    private fun loadOutingRecommendations() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            repository.getOutingRecommendations()
                .onSuccess { deliveries ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        outings = deliveries,
                        error = null
                    )
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = exception.message
                    )
                }
        }
    }

    fun retryLoading() {
        loadOutingRecommendations()
    }
}