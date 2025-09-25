package com.ms.helloworld.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ms.helloworld.dto.response.MeditationDelivery
import com.ms.helloworld.repository.MeditationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MeditationUiState(
    val isLoading: Boolean = false,
    val meditations: List<MeditationDelivery> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class MeditationViewModel @Inject constructor(
    private val repository: MeditationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MeditationUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadMeditationRecommendations()
    }

    private fun loadMeditationRecommendations() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            repository.getMeditationRecommendations()
                .onSuccess { deliveries ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        meditations = deliveries,
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
        loadMeditationRecommendations()
    }

    fun onMeditationClick(meditation: MeditationDelivery) {
        // Handle meditation selection
        viewModelScope.launch {
            // Open URL or handle navigation
        }
    }
}