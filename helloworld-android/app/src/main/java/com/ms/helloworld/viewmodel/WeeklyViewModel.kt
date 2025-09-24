package com.ms.helloworld.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ms.helloworld.dto.response.DietDay
import com.ms.helloworld.dto.response.WeeklyDietsResponse
import com.ms.helloworld.dto.response.WeeklyInfoResponse
import com.ms.helloworld.dto.response.WeeklyWorkoutsResponse
import com.ms.helloworld.dto.response.WorkoutItem
import com.ms.helloworld.repository.WeeklyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WeeklyRecommendationState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val currentWeek: Int = 1,
    val weeklyInfo: String? = null,
    val workouts: List<WorkoutItem> = emptyList(),
    val diets: List<DietDay> = emptyList()
)

@HiltViewModel
class WeeklyViewModel @Inject constructor(
    private val weeklyRepository: WeeklyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(WeeklyRecommendationState())
    val state: StateFlow<WeeklyRecommendationState> = _state.asStateFlow()

    fun loadWeeklyData(weekNo: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, currentWeek = weekNo)

            try {
                // 병렬로 데이터 로드
                val infoResult = weeklyRepository.getWeeklyInfo(weekNo)
                val workoutsResult = weeklyRepository.getWeeklyWorkouts(weekNo)
                val dietsResult = weeklyRepository.getWeeklyDiets(weekNo)

                val info = infoResult.getOrNull()?.info
                val workouts = workoutsResult.getOrNull()?.items ?: emptyList()
                val diets = dietsResult.getOrNull()?.days ?: emptyList()

                _state.value = _state.value.copy(
                    isLoading = false,
                    weeklyInfo = info,
                    workouts = workouts,
                    diets = diets,
                    errorMessage = null
                )

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "데이터 로드 실패"
                )
            }
        }
    }

    fun changeWeek(weekNo: Int) {
        if (weekNo in 1..42) {
            loadWeeklyData(weekNo)
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}