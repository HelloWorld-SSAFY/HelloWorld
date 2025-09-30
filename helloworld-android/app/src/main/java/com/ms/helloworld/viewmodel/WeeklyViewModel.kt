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
import kotlinx.coroutines.async
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

data class CachedWeeklyData(
    val weeklyInfo: String?,
    val workouts: List<WorkoutItem>,
    val diets: List<DietDay>,
    val timestamp: Long = System.currentTimeMillis()
)

@HiltViewModel
class WeeklyViewModel @Inject constructor(
    private val weeklyRepository: WeeklyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(WeeklyRecommendationState())
    val state: StateFlow<WeeklyRecommendationState> = _state.asStateFlow()

    // 캐시 저장소 (주차별로 데이터 저장)
    private val weeklyDataCache = mutableMapOf<Int, CachedWeeklyData>()

    // 캐시 유효 시간 (30분)
    private val cacheValidityDuration = 30 * 60 * 1000L

    // 현재 로딩 중인 주차들 (중복 요청 방지)
    private val loadingWeeks = mutableSetOf<Int>()

    fun loadWeeklyData(weekNo: Int) {
        // 이미 로딩 중이면 중복 요청 방지
        if (loadingWeeks.contains(weekNo)) {
            return
        }

        // 캐시된 데이터 확인
        val cachedData = getCachedData(weekNo)
        if (cachedData != null) {
            _state.value = _state.value.copy(
                isLoading = false,
                currentWeek = weekNo,
                weeklyInfo = cachedData.weeklyInfo,
                workouts = cachedData.workouts,
                diets = cachedData.diets,
                errorMessage = null
            )
            return
        }

        // 새로운 데이터 로드
        viewModelScope.launch {
            loadingWeeks.add(weekNo)
            _state.value = _state.value.copy(
                isLoading = true,
                currentWeek = weekNo,
                errorMessage = null
            )

            try {
                // 병렬로 API 호출
                val infoDeferred = async { weeklyRepository.getWeeklyInfo(weekNo) }
                val workoutsDeferred = async { weeklyRepository.getWeeklyWorkouts(weekNo) }
                val dietsDeferred = async { weeklyRepository.getWeeklyDiets(weekNo) }

                // 모든 결과 기다리기
                val infoResult = infoDeferred.await()
                val workoutsResult = workoutsDeferred.await()
                val dietsResult = dietsDeferred.await()

                // 결과 추출
                val info = infoResult.getOrNull()?.info
                val workouts = workoutsResult.getOrNull()?.items ?: emptyList()
                val diets = dietsResult.getOrNull()?.days ?: emptyList()

                // 캐시에 저장
                weeklyDataCache[weekNo] = CachedWeeklyData(
                    weeklyInfo = info,
                    workouts = workouts,
                    diets = diets
                )

                // 상태 업데이트
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
            } finally {
                loadingWeeks.remove(weekNo)
            }
        }
    }

    fun changeWeek(weekNo: Int) {
        // 유효한 주차이고 현재 주차와 다를 때만 로드
        if (weekNo in 1..42 && weekNo != _state.value.currentWeek) {
            loadWeeklyData(weekNo)
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun refreshCurrentWeek() {
        val currentWeek = _state.value.currentWeek
        clearCacheForWeek(currentWeek)
        loadWeeklyData(currentWeek)
    }

    fun clearAllCache() {
        weeklyDataCache.clear()
    }

    fun clearCacheForWeek(weekNo: Int) {
        weeklyDataCache.remove(weekNo)
    }

    private fun getCachedData(weekNo: Int): CachedWeeklyData? {
        val cached = weeklyDataCache[weekNo] ?: return null

        // 캐시 유효성 검사
        val isExpired = System.currentTimeMillis() - cached.timestamp > cacheValidityDuration

        return if (isExpired) {
            weeklyDataCache.remove(weekNo)
            null
        } else {
            cached
        }
    }

    private fun cleanExpiredCache() {
        val currentTime = System.currentTimeMillis()
        val expiredKeys = weeklyDataCache.filter { (_, data) ->
            currentTime - data.timestamp > cacheValidityDuration
        }.keys

        expiredKeys.forEach { weeklyDataCache.remove(it) }
    }

    override fun onCleared() {
        super.onCleared()
        weeklyDataCache.clear()
        loadingWeeks.clear()
    }
}