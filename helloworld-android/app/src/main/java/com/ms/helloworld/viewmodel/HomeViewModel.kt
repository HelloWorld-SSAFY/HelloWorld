package com.ms.helloworld.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ms.helloworld.dto.response.CalendarEventResponse
import com.ms.helloworld.dto.response.MomProfile
import com.ms.helloworld.repository.CalendarRepository
import com.ms.helloworld.repository.MomProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val momProfileRepository: MomProfileRepository,
    private val calendarRepository: CalendarRepository
) : ViewModel() {
    
    private val _momProfile = MutableStateFlow(
        MomProfile(
            nickname = "로딩중...",
            pregnancyWeek = 1,
            dueDate = LocalDate.now()
        )
    )
    val momProfile: StateFlow<MomProfile> = _momProfile.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _calendarEvents = MutableStateFlow<Map<String, List<CalendarEventResponse>>>(emptyMap())
    val calendarEvents: StateFlow<Map<String, List<CalendarEventResponse>>> = _calendarEvents.asStateFlow()

    // TODO: SharedPreferences나 DataStore에서 실제 사용자 정보 가져오기
    private fun getCoupleId(): Long {
        // 임시로 하드코딩, 실제로는 로그인된 사용자의 커플 ID를 가져와야 함
        return 1L
    }

    init {
        loadMomProfile()
        loadCurrentMonthEvents()
    }
    
    private fun loadMomProfile() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val profile = momProfileRepository.getMomProfile()
                if (profile != null) {
                    _momProfile.value = profile
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private fun loadCurrentMonthEvents() {
        viewModelScope.launch {
            try {
                val currentYearMonth = YearMonth.now()
                val lastDayOfMonth = currentYearMonth.lengthOfMonth()
                val from = "${currentYearMonth}-01T00:00:00Z"
                val to = "${currentYearMonth}-${lastDayOfMonth.toString().padStart(2, '0')}T23:59:59Z"

                val result = calendarRepository.getEvents(
                    coupleId = getCoupleId(),
                    from = from,
                    to = to
                )

                if (result.isSuccess) {
                    val eventsResponse = result.getOrNull()
                    val eventsByDate = eventsResponse?.content?.groupBy { event ->
                        try {
                            // ISO 8601 날짜에서 YYYY-MM-DD 추출
                            if (event.startAt.length >= 10) {
                                event.startAt.substring(0, 10)
                            } else {
                                LocalDate.now().toString()
                            }
                        } catch (e: Exception) {
                            LocalDate.now().toString()
                        }
                    }?.mapValues { (_, events) ->
                        events.sortedBy { it.orderNo ?: Int.MAX_VALUE }
                    } ?: emptyMap()

                    _calendarEvents.value = eventsByDate
                    println("📅 Home - 캘린더 이벤트 로드 완료: ${eventsByDate.values.sumOf { it.size }}개")
                } else {
                    println("❌ Home - 캘린더 이벤트 로드 실패: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                println("💥 Home - 캘린더 이벤트 로드 예외: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun refreshProfile() {
        refreshMomProfileSilently()
    }

    private fun refreshMomProfileSilently() {
        viewModelScope.launch {
            try {
                // 로딩 상태를 변경하지 않고 백그라운드에서 새로고침
                val profile = momProfileRepository.getMomProfile()
                if (profile != null) {
                    _momProfile.value = profile
                    println("🔄 HomeScreen - 프로필 silent refresh 완료: ${profile.nickname}")
                }
            } catch (e: Exception) {
                println("❌ HomeScreen - 프로필 silent refresh 실패: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun refreshCalendarEvents() {
        loadCurrentMonthEvents()
    }
}