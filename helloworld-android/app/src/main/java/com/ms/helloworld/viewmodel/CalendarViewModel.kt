package com.ms.helloworld.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ms.helloworld.dto.request.CalendarCreateRequest
import com.ms.helloworld.dto.request.CalendarUpdateRequest
import com.ms.helloworld.dto.response.CalendarEventResponse
import com.ms.helloworld.repository.CalendarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class CalendarState(
    val events: Map<String, List<CalendarEventResponse>> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedDate: String = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
    val currentDisplayMonth: String = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CalendarState())
    val state: StateFlow<CalendarState> = _state.asStateFlow()

    // TODO: SharedPreferences나 DataStore에서 실제 사용자 정보 가져오기
    private fun getCoupleId(): Long {
        // 임시로 하드코딩, 실제로는 로그인된 사용자의 커플 ID를 가져와야 함
        return 1L
    }

    private fun getWriterId(): Long {
        // 임시로 하드코딩, 실제로는 로그인된 사용자의 ID를 가져와야 함
        return 1L
    }

    init {
        loadEventsForCurrentMonth()
    }

    fun selectDate(dateKey: String) {
        _state.value = _state.value.copy(selectedDate = dateKey)
    }

    fun changeDisplayMonth(yearMonth: String) {
        _state.value = _state.value.copy(currentDisplayMonth = yearMonth)
        loadEventsForMonth(yearMonth)
    }

    fun createEvent(
        title: String,
        content: String,
        startAt: String,
        endAt: String? = null,
        isRemind: Boolean = false,
        orderNo: Int? = null
    ) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val request = CalendarCreateRequest(
                    title = title,
                    startAt = startAt,
                    endAt = endAt,
                    isRemind = isRemind,
                    memo = content,
                    orderNo = orderNo
                )

                val result = calendarRepository.createEvent(
                    coupleId = getCoupleId(),
                    writerId = getWriterId(),
                    request = request
                )

                if (result.isSuccess) {
                    // 성공 시 해당 월의 일정을 다시 로드
                    loadEventsForCurrentMonth()
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "일정 생성에 실패했습니다."
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "네트워크 오류가 발생했습니다."
                )
            }
        }
    }

    fun updateEvent(
        eventId: Long,
        title: String? = null,
        content: String? = null,
        startAt: String? = null,
        endAt: String? = null,
        isRemind: Boolean? = null,
        orderNo: Int? = null
    ) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val request = CalendarUpdateRequest(
                    title = title,
                    startAt = startAt,
                    endAt = endAt,
                    isRemind = isRemind,
                    memo = content,
                    orderNo = orderNo
                )

                val result = calendarRepository.updateEvent(eventId, request)

                if (result.isSuccess) {
                    loadEventsForCurrentMonth()
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "일정 수정에 실패했습니다."
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "네트워크 오류가 발생했습니다."
                )
            }
        }
    }

    fun deleteEvent(eventId: Long) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val result = calendarRepository.deleteEvent(eventId)

                if (result.isSuccess) {
                    loadEventsForCurrentMonth()
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "일정 삭제에 실패했습니다."
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "네트워크 오류가 발생했습니다."
                )
            }
        }
    }

    private fun loadEventsForCurrentMonth() {
        loadEventsForMonth(_state.value.currentDisplayMonth)
    }

    private fun loadEventsForMonth(yearMonth: String) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                // yearMonth는 "yyyy-MM" 형식 - 올바른 월말 날짜 계산
                val yearMonthParsed = YearMonth.parse(yearMonth)
                val lastDayOfMonth = yearMonthParsed.lengthOfMonth()
                val from = "${yearMonth}-01T00:00:00Z"
                val to = "${yearMonth}-${lastDayOfMonth.toString().padStart(2, '0')}T23:59:59Z"

                val result = calendarRepository.getEvents(
                    coupleId = getCoupleId(),
                    from = from,
                    to = to
                )

                if (result.isSuccess) {
                    val eventsResponse = result.getOrNull()
                    val eventsByDate = eventsResponse?.events?.groupBy { event ->
                        try {
                            // ISO 8601 날짜에서 YYYY-MM-DD 추출 (더 안전한 처리)
                            if (event.startAt.length >= 10) {
                                event.startAt.substring(0, 10)
                            } else {
                                // 예외 상황 처리
                                LocalDate.now().toString()
                            }
                        } catch (e: Exception) {
                            // 날짜 파싱 실패 시 오늘 날짜 사용
                            LocalDate.now().toString()
                        }
                    }?.mapValues { (_, events) ->
                        // 각 날짜별로 orderNo 기준으로 정렬
                        events.sortedBy { it.orderNo ?: Int.MAX_VALUE }
                    } ?: emptyMap()

                    _state.value = _state.value.copy(
                        events = eventsByDate,
                        isLoading = false
                    )
                } else {
                    val errorMsg = when {
                        result.exceptionOrNull()?.message?.contains("timeout", ignoreCase = true) == true ->
                            "서버 연결 시간이 초과되었습니다. 네트워크 상태를 확인해주세요."
                        result.exceptionOrNull()?.message?.contains("404", ignoreCase = true) == true ->
                            "서버를 찾을 수 없습니다. 관리자에게 문의해주세요."
                        result.exceptionOrNull()?.message?.contains("401", ignoreCase = true) == true ->
                            "인증이 필요합니다. 다시 로그인해주세요."
                        else -> result.exceptionOrNull()?.message ?: "일정 조회에 실패했습니다."
                    }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = errorMsg
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "네트워크 오류가 발생했습니다."
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun updateEventOrder(dateKey: String, reorderedEvents: List<CalendarEventResponse>) {
        viewModelScope.launch {
            try {
                // 로컬 상태 먼저 업데이트 (즉시 반영)
                val updatedEvents = _state.value.events.toMutableMap()
                val updatedEventsList = reorderedEvents.mapIndexed { index, event ->
                    event.copy(orderNo = index + 1)
                }
                updatedEvents[dateKey] = updatedEventsList

                _state.value = _state.value.copy(events = updatedEvents)

                // 백그라운드에서 서버 업데이트 (성능 최적화)
                val updateRequests = reorderedEvents.mapIndexedNotNull { index, event ->
                    val newOrderNo = index + 1
                    if (event.orderNo != newOrderNo) {
                        event.eventId to CalendarUpdateRequest(orderNo = newOrderNo)
                    } else null
                }

                // 배치로 업데이트 요청 처리
                updateRequests.forEach { (eventId, request) ->
                    val result = calendarRepository.updateEvent(eventId, request)
                    if (result.isFailure) {
                        // 개별 업데이트 실패 시 로그만 남기고 계속 진행
                        println("Failed to update event order for eventId: $eventId")
                    }
                }

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = e.message ?: "순서 변경에 실패했습니다."
                )
                // 실패 시 원래 데이터 다시 로드
                loadEventsForCurrentMonth()
            }
        }
    }
}