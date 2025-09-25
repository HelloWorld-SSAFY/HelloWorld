package com.ms.helloworld.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ms.helloworld.dto.response.MeditationDelivery
import com.ms.helloworld.dto.response.YogaDelivery
import com.ms.helloworld.repository.MeditationRepository
import com.ms.helloworld.repository.YogaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class YogaUiState(
    val isLoading: Boolean = false,
    val yogas: List<YogaDelivery> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class YogaViewModel @Inject constructor(
    private val repository: YogaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(YogaUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadYogaRecommendations()
    }

    private fun loadYogaRecommendations() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            repository.getYogaRecommendations()
                .onSuccess { deliveries ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        yogas = deliveries,
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
        loadYogaRecommendations()
    }
}