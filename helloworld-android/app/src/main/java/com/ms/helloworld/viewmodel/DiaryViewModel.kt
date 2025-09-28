package com.ms.helloworld.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ms.helloworld.dto.request.DiaryCreateRequest
import com.ms.helloworld.dto.request.DiaryUpdateRequest
import com.ms.helloworld.dto.response.DiaryResponse
import com.ms.helloworld.repository.DiaryRepository
import com.ms.helloworld.repository.CaricatureRepository
import com.ms.helloworld.dto.response.CaricatureResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val diaryRepository: DiaryRepository,
    private val caricatureRepository: CaricatureRepository,
    @ApplicationContext private val context: Context
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
    }

    fun clearDiaries() {
        _state.value = _state.value.copy(
            diaries = emptyList(),
            weeklyDiaryStatus = emptyList()
        )
    }

    fun setUserInfo(userId: Long?, userGender: String?) {
        currentUserId = userId
        currentUserGender = userGender
    }

    fun setCoupleInfo(userAId: Long?, userBId: Long?) {
        this.userAId = userAId
        this.userBId = userBId
    }

    private fun getLmpDate(): String = actualLmpDate

    private val _state = MutableStateFlow(DiaryState())
    val state: StateFlow<DiaryState> = _state.asStateFlow()

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

                } else {
                    val error = result.exceptionOrNull()?.message ?: "일기 로딩 실패"
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
                e.printStackTrace()
            }
        }
    }

    fun createDiary(title: String, content: String, targetDate: String = LocalDate.now().toString(), authorRole: String = "FEMALE", authorId: Long) {
        viewModelScope.launch {
            try {

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

                val result = diaryRepository.createDiary(request)

                if (result.isSuccess) {
                    val response = result.getOrNull()

                    // 상태 업데이트
                    _state.value = _state.value.copy(isLoading = false, errorMessage = null)

                    // 일기 목록 새로고침 - 약간의 지연 후 실행
                    kotlinx.coroutines.delay(500) // 0.5초 지연

                    // 주간 일기와 현재 상태의 일기들을 모두 새로고침
                    loadCurrentWeekDiaries()

                    // 현재 일기 상태를 바로 업데이트 (등록된 일기 포함)
                    val updatedDiaries = _state.value.diaries.toMutableList()
                    response?.let { newDiary ->
                        updatedDiaries.add(newDiary)
                        _state.value = _state.value.copy(diaries = updatedDiaries)
                    }
                } else {
                    val exception = result.exceptionOrNull()

                    val error = exception?.message ?: "일기 생성 실패"
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = error
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()

                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "네트워크 오류"
                )
            }
        }
    }

    fun createDiaryWithFiles(
        title: String,
        content: String,
        targetDate: String,
        authorRole: String,
        authorId: Long,
        imageUris: List<Uri>,
        ultrasounds: List<Boolean>
    ) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val result = diaryRepository.createDiaryWithFiles(
                    context = context,
                    entryDate = targetDate,
                    diaryTitle = title,
                    diaryContent = content,
                    targetDate = targetDate,
                    authorRole = authorRole,
                    authorId = authorId,
                    imageUris = imageUris,
                    ultrasounds = ultrasounds
                )

                _state.value = _state.value.copy(isLoading = false)

                if (result.isSuccess) {
                    val response = result.getOrNull()

                    // 성공 시 일기 목록을 다시 로드하여 상태 업데이트
                    val updatedDiaries = _state.value.diaries.toMutableList()
                    response?.let { newDiary ->
                        updatedDiaries.add(newDiary)
                        _state.value = _state.value.copy(diaries = updatedDiaries)
                    }
                } else {
                    val exception = result.exceptionOrNull()

                    val error = exception?.message ?: "Multipart 일기 생성 실패"
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = error
                    )
                }
            } catch (e: Exception) {
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

    fun loadDiary(diaryId: Long) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val result = diaryRepository.getDiary(diaryId)
                if (result.isSuccess) {
                    val diary = result.getOrNull()
                    if (diary != null) {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            diaries = listOf(diary)
                        )
                    } else {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            errorMessage = "일기를 찾을 수 없습니다."
                        )
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "일기 로딩 실패"
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = error
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "일기 로딩 중 오류: ${e.message}"
                )
            }
        }
    }

    fun loadDiariesByDay(day: Int, lmpDate: String) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val result = diaryRepository.getDiariesByDay(day, lmpDate)
                if (result.isSuccess) {
                    val diariesResponse = result.getOrNull()
                    val diaries = diariesResponse?.content ?: emptyList()

                    _state.value = _state.value.copy(
                        isLoading = false,
                        diaries = diaries
                    )
                } else {
                    val error = result.exceptionOrNull()?.message ?: "일기 로딩 실패"
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
    }

    // 수정할 일기 클리어
    fun clearEditingDiary() {
        _state.value = _state.value.copy(editingDiary = null)
    }

    // 캐리커쳐 조회
    suspend fun getCaricatureFromPhoto(diaryPhotoId: Long): Result<CaricatureResponse?> {
        return try {
            caricatureRepository.getCaricatureFromPhoto(diaryPhotoId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 캐리커쳐 생성
    suspend fun generateCaricature(diaryPhotoId: Long): Result<CaricatureResponse> {
        return try {
            caricatureRepository.generateCaricature(diaryPhotoId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 임신 주차 계산 (네겔레 법칙 기반)
    private fun getCurrentPregnancyWeek(currentDate: LocalDate): Int {
        return try {
            val lmpDate = LocalDate.parse(getLmpDate())
            val daysSinceLastPeriod = ChronoUnit.DAYS.between(lmpDate, currentDate)
            val pregnancyWeek = ((daysSinceLastPeriod / 7) + 1).toInt()

            // 임신 주차는 1~42주 범위로 제한
            pregnancyWeek.coerceIn(1, 42)
        } catch (e: Exception) {
            1 // 기본값으로 1주차 반환
        }
    }

    // 주간 일기 상태 생성
    private fun createWeeklyStatus(week: Int, diaries: List<DiaryResponse>): List<WeeklyDiaryStatus> {
        // 임신 주차를 기반으로 날짜 범위 계산
        val lmpDate = LocalDate.parse(getLmpDate())

        // 서버와 동일한 주차 계산: week주차 = LMP + (week-1) * 7 + 1일
        val weekStartDay = (week - 1) * 7 + 1 // 해당 주차의 첫 번째 날 (임신 일수)
        val startOfWeek = lmpDate.plusDays(weekStartDay.toLong()) // LMP + weekStartDay일 (서버 방식)

        // 서버 응답의 일기들을 targetDate별로 그룹화
        val diariesByDate = diaries.groupBy { it.targetDate }

        diariesByDate.forEach { (date, dateDiaries) ->
        }

        // 주간 7일 각각에 대해 상태 생성
        return (0..6).map { dayOffset ->
            val targetDate = startOfWeek.plusDays(dayOffset.toLong())
            val targetDateString = targetDate.toString() // "yyyy-MM-dd" format
            val dayInWeek = dayOffset + 1 // 1~7 (일요일=1, 토요일=7)

            val dayDiaries = diariesByDate[targetDateString] ?: emptyList()

            // 디버깅: 각 날짜별 일기 확인
            dayDiaries.forEachIndexed { idx, diary ->
                val inferredRole = diary.inferAuthorRole(currentUserId, currentUserGender)
            }

            val momDiary = dayDiaries.find {
                it.inferAuthorRole(currentUserId, currentUserGender, userAId, userBId) == "FEMALE"
            }
            val dadDiary = dayDiaries.find {
                it.inferAuthorRole(currentUserId, currentUserGender, userAId, userBId) == "MALE"
            }

            WeeklyDiaryStatus(
                day = dayInWeek, // 1~7: 요일별 원의 위치
                date = targetDate,
                momWritten = momDiary != null,
                dadWritten = dadDiary != null,
                momDiary = momDiary,
                dadDiary = dadDiary
            )
        }
    }
}