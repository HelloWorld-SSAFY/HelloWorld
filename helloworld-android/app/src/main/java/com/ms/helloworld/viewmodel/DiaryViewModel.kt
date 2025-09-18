package com.ms.helloworld.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ms.helloworld.dto.request.DiaryCreateRequest
import com.ms.helloworld.dto.request.DiaryUpdateRequest
import com.ms.helloworld.dto.response.DiaryResponse
import com.ms.helloworld.repository.DiaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.*
import javax.inject.Inject

data class DiaryState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val diaries: List<DiaryResponse> = emptyList(),
    val currentWeek: Int = 1,
    val weeklyDiaryStatus: List<WeeklyDiaryStatus> = emptyList()
)

data class WeeklyDiaryStatus(
    val day: Int,
    val date: LocalDate,
    val momWritten: Boolean = false,
    val dadWritten: Boolean = false,
    val momDiary: DiaryResponse? = null,
    val dadDiary: DiaryResponse? = null
)

@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val diaryRepository: DiaryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DiaryState())
    val state: StateFlow<DiaryState> = _state.asStateFlow()

    init {
        loadCurrentWeekDiaries()
    }

    fun loadCurrentWeekDiaries() {
        val currentDate = LocalDate.now()
        val currentWeek = getCurrentPregnancyWeek(currentDate)
        loadWeeklyDiaries(currentWeek)
    }

    fun loadWeeklyDiaries(week: Int) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)
                println("📅 DiaryViewModel - 주간 일기 로딩: ${week}주차")

                val result = diaryRepository.getWeeklyDiaries(week)
                if (result.isSuccess) {
                    val diariesResponse = result.getOrNull()
                    val diaries = diariesResponse?.content ?: emptyList()

                    // 주간 일기 상태 생성 (7일간)
                    val weeklyStatus = createWeeklyStatus(week, diaries)

                    _state.value = _state.value.copy(
                        isLoading = false,
                        diaries = diaries,
                        currentWeek = week,
                        weeklyDiaryStatus = weeklyStatus
                    )

                    println("✅ DiaryViewModel - 주간 일기 로딩 완료: ${diaries.size}개")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "일기 로딩 실패"
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = error
                    )
                    println("❌ DiaryViewModel - 주간 일기 로딩 실패: $error")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "네트워크 오류"
                )
                println("💥 DiaryViewModel - 예외 발생: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun createDiary(title: String, content: String) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val request = DiaryCreateRequest(
                    diaryTitle = title,
                    diaryContent = content
                )

                val result = diaryRepository.createDiary(request)
                if (result.isSuccess) {
                    println("✅ DiaryViewModel - 일기 생성 성공")
                    // 현재 주차 일기 다시 로드
                    loadCurrentWeekDiaries()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "일기 생성 실패"
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = error
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "네트워크 오류"
                )
            }
        }
    }

    fun updateDiary(diaryId: Long, title: String, content: String) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val request = DiaryUpdateRequest(
                    diaryTitle = title,
                    diaryContent = content
                )

                val result = diaryRepository.updateDiary(diaryId, request)
                if (result.isSuccess) {
                    println("✅ DiaryViewModel - 일기 수정 성공")
                    loadCurrentWeekDiaries()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "일기 수정 실패"
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = error
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "네트워크 오류"
                )
            }
        }
    }

    fun deleteDiary(diaryId: Long) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val result = diaryRepository.deleteDiary(diaryId)
                if (result.isSuccess) {
                    println("✅ DiaryViewModel - 일기 삭제 성공")
                    loadCurrentWeekDiaries()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "일기 삭제 실패"
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = error
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "네트워크 오류"
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    // 임신 주차 계산 (임시 구현)
    private fun getCurrentPregnancyWeek(currentDate: LocalDate): Int {
        // TODO: 실제 임신 시작일 기준으로 계산
        // 현재는 임시로 연초부터의 주차 계산
        val weekFields = WeekFields.of(Locale.getDefault())
        return currentDate.get(weekFields.weekOfYear())
    }

    // 주간 일기 상태 생성
    private fun createWeeklyStatus(week: Int, diaries: List<DiaryResponse>): List<WeeklyDiaryStatus> {
        return (1..7).map { day ->
            val dayDiaries = diaries.filter { diary ->
                // TODO: 날짜 필터링 로직 구현
                true // 임시
            }

            val momDiary = dayDiaries.find { it.authorRole == "MOTHER" }
            val dadDiary = dayDiaries.find { it.authorRole == "FATHER" }

            WeeklyDiaryStatus(
                day = day,
                date = LocalDate.now().plusDays(day.toLong() - 1), // 임시
                momWritten = momDiary != null,
                dadWritten = dadDiary != null,
                momDiary = momDiary,
                dadDiary = dadDiary
            )
        }
    }
}