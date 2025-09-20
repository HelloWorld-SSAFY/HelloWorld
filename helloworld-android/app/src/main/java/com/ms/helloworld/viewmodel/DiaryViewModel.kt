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

    // TODO: SharedPreferences나 DataStore에서 실제 사용자 정보 가져오기
    private fun getCoupleId(): Long {
        // 임시로 하드코딩, 실제로는 로그인된 사용자의 커플 ID를 가져와야 함
        return 1L
    }

    private fun getLmpDate(): String {
        // 임시로 하드코딩, 실제로는 MomProfile에서 가져와야 함
        return "2025-02-02" // yyyy-MM-dd 형식 (스웨거와 동일)
    }

    private val _state = MutableStateFlow(DiaryState())
    val state: StateFlow<DiaryState> = _state.asStateFlow()

    init {
        // DiaryScreen에서 실제 임신 주차로 loadWeeklyDiaries를 호출하므로
        // 여기서는 자동 로딩하지 않음
        println("📝 DiaryViewModel - 초기화 완료, 수동 로딩 대기 중")
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
                val coupleId = getCoupleId()
                val lmpDate = getLmpDate()
                println("📅 DiaryViewModel - 주간 일기 로딩: ${week}주차")
                println("📅 DiaryViewModel - API 파라미터: coupleId=$coupleId, week=$week, lmpDate=$lmpDate")
                println("📅 DiaryViewModel - 예상 URL: /calendar/diary/week?coupleId=$coupleId&week=$week&lmpDate=$lmpDate")

                // 새로운 API 사용: calendar/diary/week
                val result = diaryRepository.getDiariesByWeek(
                    coupleId = coupleId,
                    week = week,
                    lmpDate = lmpDate
                )

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

    fun createDiary(title: String, content: String, targetDate: String = LocalDate.now().toString(), authorRole: String = "FEMALE", authorId: Long = 1L, coupleId: Long = 1L) {
        viewModelScope.launch {
            try {
                println("🚀 DiaryViewModel - createDiary 시작")
                println("📝 입력 파라미터:")
                println("  - title: '$title'")
                println("  - content: '$content'")
                println("  - targetDate: '$targetDate'")
                println("  - authorRole: '$authorRole'")
                println("  - authorId: $authorId")
                println("  - coupleId: $coupleId")

                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val currentDate = LocalDate.now().toString()

                val request = DiaryCreateRequest(
                    entryDate = currentDate,
                    diaryTitle = title,
                    diaryContent = content,
                    imageUrl = "", // 임시로 빈 문자열
                    coupleId = coupleId,
                    authorId = authorId,
                    authorRole = authorRole,
                    targetDate = targetDate
                )

                println("📦 DiaryCreateRequest 생성:")
                println("  - entryDate: '${request.entryDate}'")
                println("  - diaryTitle: '${request.diaryTitle}'")
                println("  - diaryContent: '${request.diaryContent}'")
                println("  - imageUrl: '${request.imageUrl}'")
                println("  - coupleId: ${request.coupleId}")
                println("  - authorId: ${request.authorId}")
                println("  - authorRole: '${request.authorRole}'")
                println("  - targetDate: '${request.targetDate}'")

                val result = diaryRepository.createDiary(request)

                if (result.isSuccess) {
                    val response = result.getOrNull()
                    println("✅ DiaryViewModel - 일기 생성 성공!")
                    println("📋 생성된 일기 정보:")
                    println("  - diaryId: ${response?.diaryId}")
                    println("  - diaryTitle: ${response?.diaryTitle}")
                    println("  - authorRole: ${response?.authorRole}")

                    // 상태 업데이트
                    _state.value = _state.value.copy(isLoading = false, errorMessage = null)

                    // 일기 목록 새로고침 - 약간의 지연 후 실행
                    println("🔄 DiaryViewModel - 일기 목록 새로고침 시작")
                    kotlinx.coroutines.delay(500) // 0.5초 지연

                    // 주간 일기와 현재 상태의 일기들을 모두 새로고침
                    loadCurrentWeekDiaries()

                    // 현재 일기 상태를 바로 업데이트 (등록된 일기 포함)
                    val updatedDiaries = _state.value.diaries.toMutableList()
                    response?.let { newDiary ->
                        updatedDiaries.add(newDiary)
                        _state.value = _state.value.copy(diaries = updatedDiaries)
                        println("📋 DiaryViewModel - 새 일기가 상태에 추가됨: ${newDiary.diaryId}")
                    }
                } else {
                    val exception = result.exceptionOrNull()
                    println("❌ DiaryViewModel - 일기 생성 실패")
                    println("  - Exception: ${exception?.javaClass?.simpleName}")
                    println("  - Message: ${exception?.message}")

                    val error = exception?.message ?: "일기 생성 실패"
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = error
                    )
                }
            } catch (e: Exception) {
                println("💥 DiaryViewModel - createDiary 예외 발생")
                println("  - Exception type: ${e.javaClass.simpleName}")
                println("  - Exception message: ${e.message}")
                e.printStackTrace()

                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "네트워크 오류"
                )
            }
        }
    }

    fun updateDiary(diaryId: Long, title: String, content: String, targetDate: String) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val request = DiaryUpdateRequest(
                    diaryTitle = title,
                    diaryContent = content,
                    targetDate = targetDate
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

    fun loadDiariesByDay(coupleId: Long, day: Int, lmpDate: String) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)
                println("📆 DiaryViewModel - 일별 일기 로딩: ${day}일차")

                val result = diaryRepository.getDiariesByDay(coupleId, day, lmpDate)
                if (result.isSuccess) {
                    val diariesResponse = result.getOrNull()
                    val diaries = diariesResponse?.content ?: emptyList()

                    _state.value = _state.value.copy(
                        isLoading = false,
                        diaries = diaries
                    )

                    println("✅ DiaryViewModel - 일별 일기 로딩 완료: ${diaries.size}개")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "일기 로딩 실패"
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = error
                    )
                    println("❌ DiaryViewModel - 일별 일기 로딩 실패: $error")
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

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    // 디버깅용: 전체 일기 조회
    fun loadAllDiariesForDebug() {
        viewModelScope.launch {
            try {
                println("🔍 DiaryViewModel - 디버깅용 전체 일기 조회 시작")
                val result = diaryRepository.getDiaries(page = 0, size = 100)
                if (result.isSuccess) {
                    val response = result.getOrNull()
                    println("🔍 전체 일기 조회 성공: ${response?.content?.size ?: 0}개")
                } else {
                    println("🔍 전체 일기 조회 실패: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                println("🔍 전체 일기 조회 예외: ${e.message}")
            }
        }
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
        // 현재 주의 시작 날짜 계산 (월요일부터 시작)
        val today = LocalDate.now()
        val weekFields = WeekFields.of(Locale.getDefault())
        val currentWeek = today.get(weekFields.weekOfYear())
        val weekOffset = week - currentWeek
        val startOfWeek = today.with(weekFields.dayOfWeek(), 1).plusWeeks(weekOffset.toLong())

        return (0..6).map { dayOffset ->
            val targetDate = startOfWeek.plusDays(dayOffset.toLong())
            val targetDateString = targetDate.toString() // "yyyy-MM-dd" format

            val dayDiaries = diaries.filter { diary ->
                diary.targetDate == targetDateString
            }

            val momDiary = dayDiaries.find { it.authorRole == "FEMALE" }
            val dadDiary = dayDiaries.find { it.authorRole == "MALE" }

            WeeklyDiaryStatus(
                day = dayOffset + 1,
                date = targetDate,
                momWritten = momDiary != null,
                dadWritten = dadDiary != null,
                momDiary = momDiary,
                dadDiary = dadDiary
            )
        }
    }
}