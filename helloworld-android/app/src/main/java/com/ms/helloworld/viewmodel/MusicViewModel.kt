package com.ms.helloworld.viewmodel

import com.ms.helloworld.dto.response.MusicDelivery
import com.ms.helloworld.repository.MusicRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MusicUiState(
    val isLoading: Boolean = false,
    val musics: List<MusicDelivery> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadMusicRecommendations()
    }

    private fun loadMusicRecommendations() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            repository.getMusicRecommendations()
                .onSuccess { deliveries ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        musics = deliveries,
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
        loadMusicRecommendations()
    }
}