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
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.*
import javax.inject.Inject

data class DiaryState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val diaries: List<DiaryResponse> = emptyList(),
    val currentWeek: Int = 1,
    val weeklyDiaryStatus: List<WeeklyDiaryStatus> = emptyList(),
    val editingDiary: DiaryResponse? = null // 수정할 일기 데이터
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

    // LMP 날짜는 외부에서 설정
    private var actualLmpDate: String = "2025-02-02"

    // 사용자 정보는 외부에서 설정
    private var currentUserId: Long? = null
    private var currentUserGender: String? = null
    private var userAId: Long? = null
    private var userBId: Long? = null

    fun setLmpDate(lmpDate: String) {
        actualLmpDate = lmpDate
        println("📝 DiaryViewModel - LMP 날짜 업데이트: lmpDate=$lmpDate")
    }

    fun clearDiaries() {
        _state.value = _state.value.copy(diaries = emptyList())
        println("🧹 DiaryViewModel - 일기 데이터 초기화")
    }

    fun setUserInfo(userId: Long?, userGender: String?) {
        currentUserId = userId
        currentUserGender = userGender
        println("📝 DiaryViewModel - 사용자 정보 업데이트: userId=$userId, userGender=$userGender")
    }

    fun setCoupleInfo(userAId: Long?, userBId: Long?) {
        this.userAId = userAId
        this.userBId = userBId
        println("📝 DiaryViewModel - 커플 정보 업데이트: userAId=$userAId, userBId=$userBId")
    }

    private fun getLmpDate(): String = actualLmpDate

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
                val lmpDate = getLmpDate()
                println("📅 DiaryViewModel - 주간 일기 로딩: ${week}주차")
                println("📅 DiaryViewModel - API 파라미터: week=$week, lmpDate=$lmpDate")

                // 새로운 API 사용: calendar/diary/week
                val result = diaryRepository.getDiariesByWeek(
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

    fun createDiary(title: String, content: String, targetDate: String = LocalDate.now().toString(), authorRole: String = "FEMALE", authorId: Long) {
        viewModelScope.launch {
            try {
                println("🚀 DiaryViewModel - createDiary 시작")
                println("📝 입력 파라미터:")
                println("  - title: '$title'")
                println("  - content: '$content'")
                println("  - targetDate: '$targetDate'")
                println("  - authorRole: '$authorRole'")
                println("  - authorId: $authorId")

                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val currentDate = LocalDate.now().toString()
                val currentDateTime = java.time.LocalDateTime.now().toString()

                val request = DiaryCreateRequest(
                    entryDate = currentDate,
                    diaryTitle = title,
                    diaryContent = content,
                    imageUrl = "", // 임시로 빈 문자열
                    coupleId = 0L, // 서버에서 토큰으로 처리하므로 의미없는 값
                    authorId = authorId,
                    authorRole = authorRole,
                    targetDate = targetDate,
                    createdAt = currentDateTime,
                    updatedAt = currentDateTime
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

    fun updateDiary(diaryId: Long, title: String, content: String, targetDate: String, imageUrl: String = "") {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val request = DiaryUpdateRequest(
                    entryDate = targetDate,
                    diaryTitle = title,
                    diaryContent = content,
                    imageUrl = imageUrl
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

    fun loadDiariesByDay(day: Int, lmpDate: String) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)
                println("📆 DiaryViewModel - 일별 일기 로딩: ${day}일차")

                val result = diaryRepository.getDiariesByDay(day, lmpDate)
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

    // 수정할 일기 설정
    fun setEditingDiary(diary: DiaryResponse) {
        _state.value = _state.value.copy(editingDiary = diary)
        println("📝 DiaryViewModel - 편집할 일기 설정: ID=${diary.diaryId}, 제목='${diary.diaryTitle}'")
    }

    // 수정할 일기 클리어
    fun clearEditingDiary() {
        _state.value = _state.value.copy(editingDiary = null)
        println("🧹 DiaryViewModel - 편집 일기 클리어")
    }

    // TODO: 나중에 필요시 전체 일기 조회 기능 추가
    // fun loadAllDiariesForDebug() { ... }

    // 임신 주차 계산 (네겔레 법칙 기반)
    private fun getCurrentPregnancyWeek(currentDate: LocalDate): Int {
        return try {
            val lmpDate = LocalDate.parse(getLmpDate())
            val daysSinceLastPeriod = ChronoUnit.DAYS.between(lmpDate, currentDate)
            val pregnancyWeek = ((daysSinceLastPeriod / 7) + 1).toInt()

            // 임신 주차는 1~42주 범위로 제한
            pregnancyWeek.coerceIn(1, 42)
        } catch (e: Exception) {
            println("❌ DiaryViewModel - LMP 날짜 파싱 실패, 기본값 1주차 반환: ${e.message}")
            1 // 기본값으로 1주차 반환
        }
    }

    // 주간 일기 상태 생성
    private fun createWeeklyStatus(week: Int, diaries: List<DiaryResponse>): List<WeeklyDiaryStatus> {
        // 임신 주차를 기반으로 날짜 범위 계산
        val lmpDate = LocalDate.parse(getLmpDate())

        // 임신 주차 계산: week주차 = LMP + (week-1) * 7일
        val weekStartDay = (week - 1) * 7 + 1 // 해당 주차의 첫 번째 날 (임신 일수)
        val startOfWeek = lmpDate.plusDays((weekStartDay - 1).toLong()) // LMP + (일수-1)

        println("📅 DiaryViewModel - createWeeklyStatus: ${week}주차")
        println("  - LMP 날짜: $lmpDate")
        println("  - 주차 시작일: ${weekStartDay}일차")
        println("  - 주간 시작 날짜: $startOfWeek")
        println("  - 주간 종료 날짜: ${startOfWeek.plusDays(6)}")

        return (0..6).map { dayOffset ->
            val targetDate = startOfWeek.plusDays(dayOffset.toLong())
            val targetDateString = targetDate.toString() // "yyyy-MM-dd" format

            val dayDiaries = diaries.filter { diary ->
                diary.targetDate == targetDateString
            }

            // 디버깅: 각 날짜별 일기 확인
            println("📅 DiaryViewModel - calculateWeeklyStatus: ${targetDateString}")
            println("  - 해당 날짜 일기 수: ${dayDiaries.size}")
            dayDiaries.forEachIndexed { idx, diary ->
                val inferredRole = diary.inferAuthorRole(currentUserId, currentUserGender)
                println("    [$idx] ID=${diary.diaryId}, 제목=${diary.diaryTitle}, inferredRole=$inferredRole")
                println("    [$idx] authorId=${diary.authorId}, authorRole=${diary.authorRole}")
                println("    [$idx] currentUserId=$currentUserId, currentUserGender=$currentUserGender")
            }

            val momDiary = dayDiaries.find {
                it.inferAuthorRole(currentUserId, currentUserGender, userAId, userBId) == "FEMALE"
            }
            val dadDiary = dayDiaries.find {
                it.inferAuthorRole(currentUserId, currentUserGender, userAId, userBId) == "MALE"
            }

            println("  - momDiary found: ${momDiary != null}")
            println("  - dadDiary found: ${dadDiary != null}")

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