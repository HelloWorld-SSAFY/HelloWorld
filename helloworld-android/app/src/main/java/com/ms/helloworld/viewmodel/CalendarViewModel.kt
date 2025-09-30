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
                    coupleId = 0L, // 서버에서 토큰으로 처리
                    writerId = 0L, // 서버에서 토큰으로 처리
                    request = request
                )

                if (result.isSuccess) {
                    // 성공 시 해당 월의 일정을 다시 로드하여 상태 업데이트
                    _state.value = _state.value.copy(isLoading = false)
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
                // orderNo만 변경하는 경우는 로딩 상태를 표시하지 않음 (UX 개선)
                val isOrderOnlyUpdate = title == null && content == null && startAt == null && endAt == null && isRemind == null && orderNo != null

                if (!isOrderOnlyUpdate) {
                    _state.value = _state.value.copy(isLoading = true, errorMessage = null)
                }

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
                    // 성공 시 서버에서 최신 데이터 로드하여 상태 업데이트
                    if (!isOrderOnlyUpdate) {
                        _state.value = _state.value.copy(isLoading = false)
                    }
                    loadEventsForCurrentMonth()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "일정 수정에 실패했습니다."

                    // 실패 시 원래 상태로 되돌리기
                    loadEventsForCurrentMonth()

                    // 사용자에게 더 명확한 에러 메시지 제공
                    val userFriendlyMessage = when {
                        errorMsg.contains("500") -> "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
                        errorMsg.contains("400") -> "입력한 정보에 오류가 있습니다. 다시 확인해주세요."
                        errorMsg.contains("401") -> "로그인이 필요합니다."
                        errorMsg.contains("403") -> "수정 권한이 없습니다."
                        errorMsg.contains("404") -> "해당 일정을 찾을 수 없습니다."
                        else -> "일정 수정에 실패했습니다: $errorMsg"
                    }

                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = userFriendlyMessage
                    )
                }

                if (!isOrderOnlyUpdate) {
                    _state.value = _state.value.copy(isLoading = false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // 실패 시 원래 상태로 되돌리기
                loadEventsForCurrentMonth()
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
                    // 삭제 성공 후 서버에서 최신 데이터 다시 로드
                    _state.value = _state.value.copy(isLoading = false)
                    loadEventsForCurrentMonth()
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "일정 삭제에 실패했습니다."
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "네트워크 오류가 발생했습니다."
                )
            }
        }
    }

    fun loadEventsForCurrentMonth() {
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
                    from = from,
                    to = to
                )

                if (result.isSuccess) {
                    val eventsResponse = result.getOrNull()
                    val eventsByDate = eventsResponse?.content?.groupBy { event ->
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

    private fun updateLocalEvent(
        eventId: Long,
        title: String?,
        content: String?,
        startAt: String?,
        endAt: String?,
        isRemind: Boolean?,
        orderNo: Int?
    ) {
        val currentEvents = _state.value.events.toMutableMap()
        currentEvents.forEach { (dateKey, events) ->
            val eventIndex = events.indexOfFirst { it.eventId == eventId }
            if (eventIndex != -1) {
                val event = events[eventIndex]
                val updatedEvent = event.copy(
                    title = title ?: event.title,
                    memo = content ?: event.memo,
                    startAt = startAt ?: event.startAt,
                    endAt = endAt ?: event.endAt,
                    remind = isRemind ?: event.remind,
                    orderNo = orderNo ?: event.orderNo
                )
                val updatedEvents = events.toMutableList()
                updatedEvents[eventIndex] = updatedEvent
                currentEvents[dateKey] = updatedEvents.sortedBy { it.orderNo ?: Int.MAX_VALUE }
                _state.value = _state.value.copy(events = currentEvents.toMap())
                return
            }
        }
    }

    private fun removeLocalEvent(eventId: Long) {
        val currentEvents = _state.value.events.toMutableMap()
        var found = false

        currentEvents.forEach { (dateKey, events) ->
            val filteredEvents = events.filter { it.eventId != eventId }
            if (filteredEvents.size != events.size) {
                currentEvents[dateKey] = filteredEvents
                found = true
            }
        }

        if (found) {
            _state.value = _state.value.copy(events = currentEvents.toMap())
        } else {
        }
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