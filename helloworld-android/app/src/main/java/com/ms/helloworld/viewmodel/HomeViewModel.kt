package com.ms.helloworld.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ms.helloworld.dto.response.CalendarEventResponse
import com.ms.helloworld.dto.response.MomProfile
import com.ms.helloworld.dto.response.MemberProfile
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

    private val viewModelId = System.currentTimeMillis().toString().takeLast(4)

    init {
        println("🏗️ HomeViewModel 생성됨 - ID: $viewModelId")
    }
    
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

    private val _userGender = MutableStateFlow<String?>(null)
    val userGender: StateFlow<String?> = _userGender.asStateFlow()

    private val _userId = MutableStateFlow<Long?>(null)
    val userId: StateFlow<Long?> = _userId.asStateFlow()

    private val _coupleId = MutableStateFlow<Long?>(null)
    val coupleId: StateFlow<Long?> = _coupleId.asStateFlow()

    // 임시 테스트용 - API 호출이 실패할 경우 기본값 설정
    private fun setTestGender() {
        println("🧪 HomeViewModel($viewModelId) - 테스트용 성별 설정: FEMALE")
        _userGender.value = "FEMALE" // 임시로 여성으로 설정
    }

    // TODO: SharedPreferences나 DataStore에서 실제 사용자 정보 가져오기
    private fun getCoupleId(): Long {
        // 임시로 하드코딩, 실제로는 로그인된 사용자의 커플 ID를 가져와야 함
        return 1L
    }

    init {
        loadMomProfile()
        loadUserGender()
        loadCurrentMonthEvents()
    }
    
    private fun loadMomProfile() {
        viewModelScope.launch {
            try {
                println("🚀 HomeViewModel($viewModelId) - loadMomProfile 시작")
                _isLoading.value = true
                val profile = momProfileRepository.getMomProfile()
                if (profile != null) {
                    println("🚀 HomeViewModel($viewModelId) - API에서 받은 데이터: 주차=${profile.pregnancyWeek}, 닉네임=${profile.nickname}")

                    // StateFlow 강제 업데이트 - 새로운 객체로 교체
                    val newProfile = profile.copy()
                    _momProfile.value = newProfile

                    println("🚀 HomeViewModel($viewModelId) - _momProfile.value 업데이트 완료: ${_momProfile.value.pregnancyWeek}주차")
                    println("🚀 HomeViewModel($viewModelId) - StateFlow 현재값: ${momProfile.value.pregnancyWeek}주차")
                } else {
                    println("❌ HomeViewModel($viewModelId) - API에서 null 데이터 받음")
                }
            } catch (e: Exception) {
                println("💥 HomeViewModel - loadMomProfile 예외: ${e.message}")
                e.printStackTrace()
            } finally {
                _isLoading.value = false
                println("🏁 HomeViewModel - loadMomProfile 완료")
            }
        }
    }

    private fun loadUserGender() {
        viewModelScope.launch {
            try {
                println("🚀 HomeViewModel($viewModelId) - loadUserGender 시작")
                val userInfo = momProfileRepository.getUserInfo()
                println("🚻 HomeViewModel($viewModelId) - 전체 사용자 정보: $userInfo")
                println("🚻 HomeViewModel($viewModelId) - member 정보: ${userInfo.member}")
                println("🚻 HomeViewModel($viewModelId) - couple 정보: ${userInfo.couple}")

                val gender = userInfo.member.gender
                val userId = userInfo.member.id
                val coupleId = userInfo.couple?.id

                println("🚻 HomeViewModel($viewModelId) - 원본 성별: $gender")
                println("🚻 HomeViewModel($viewModelId) - 사용자 ID: $userId")
                println("🚻 HomeViewModel($viewModelId) - 커플 ID: $coupleId")

                _userGender.value = gender
                _userId.value = userId
                _coupleId.value = coupleId

                println("🚻 HomeViewModel($viewModelId) - StateFlow 저장 완료")
            } catch (e: Exception) {
                println("💥 HomeViewModel - loadUserGender 예외: ${e.message}")
                e.printStackTrace()
                // API 호출 실패 시 임시로 테스트 성별 설정
                setTestGender()
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
        println("🔄 HomeViewModel - refreshProfile() 호출됨")
        refreshMomProfileSilently()
    }

    fun forceRefreshProfile() {
        println("💪 HomeViewModel($viewModelId) - forceRefreshProfile() 강제 새로고침 시작")
        loadMomProfile() // 강제로 전체 로딩 프로세스 다시 실행
        loadUserGender() // 성별 정보도 다시 로드
    }

    private fun refreshMomProfileSilently() {
        viewModelScope.launch {
            try {
                println("🔄 HomeViewModel - refreshMomProfileSilently 시작")
                // 로딩 상태를 변경하지 않고 백그라운드에서 새로고침
                val profile = momProfileRepository.getMomProfile()
                if (profile != null) {
                    println("🔄 HomeViewModel - 새 프로필 데이터: 주차=${profile.pregnancyWeek}, 닉네임=${profile.nickname}")
                    _momProfile.value = profile
                    println("🔄 HomeViewModel - _momProfile 상태 업데이트 완료")
                } else {
                    println("❌ HomeViewModel - 프로필 데이터가 null입니다")
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