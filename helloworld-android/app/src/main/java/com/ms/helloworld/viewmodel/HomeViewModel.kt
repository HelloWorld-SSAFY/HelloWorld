package com.ms.helloworld.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ms.helloworld.dto.response.CalendarEventResponse
import com.ms.helloworld.dto.response.MomProfile
import com.ms.helloworld.repository.CalendarRepository
import com.ms.helloworld.repository.MomProfileRepository
import com.ms.helloworld.repository.StepsRepository
import com.ms.helloworld.util.LocationManager
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
    private val calendarRepository: CalendarRepository,
    val stepsRepository: StepsRepository,
    val locationManager: LocationManager
) : ViewModel() {

    private val viewModelId = System.currentTimeMillis().toString().takeLast(4)

    private val _momProfile = MutableStateFlow<MomProfile>(
        MomProfile(
            nickname = "로딩중",
            pregnancyWeek = 1,
            dueDate = LocalDate.now(),
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

    private val _menstrualDate = MutableStateFlow<String?>(null)
    val menstrualDate: StateFlow<String?> = _menstrualDate.asStateFlow()

    private val _currentPregnancyDay = MutableStateFlow<Int>(1)
    val currentPregnancyDay: StateFlow<Int> = _currentPregnancyDay.asStateFlow()

    init {
        // ViewModel 생성 시 초기 데이터 로딩
        loadUserGender() // 사용자 정보 로딩 (내부에서 커플 정보도 로딩)
        loadMomProfile() // 프로필 정보 로딩
        loadCurrentMonthEvents() // 캘린더 이벤트 로딩
    }

    // 임시 테스트용 - API 호출이 실패할 경우 기본값 설정
    private fun setTestGender() {
        _userGender.value = "FEMALE" // 임시로 여성으로 설정
    }
    
    private fun loadMomProfile() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val profile = momProfileRepository.getHomeProfileData()
                if (profile != null) {

                    // StateFlow 강제 업데이트 - 새로운 객체로 교체
                    val newProfile = profile.copy()
                    _momProfile.value = newProfile

                } else {
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadUserGender() {
        viewModelScope.launch {
            try {
                android.util.Log.d("HomeViewModel", "사용자 정보 로딩 시작...")
                val userInfo = momProfileRepository.getUserInfo()

                val gender = userInfo.member.gender
                val userId = userInfo.member.id

                android.util.Log.d("HomeViewModel", "사용자 정보 로딩 성공: gender=$gender, userId=$userId")

                _userGender.value = gender
                _userId.value = userId

                // 커플 정보는 별도 API에서 가져오기
                loadCoupleInfo()

            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "사용자 정보 로딩 중 에러 발생", e)
                e.printStackTrace()
                // API 호출 실패 시 임시로 테스트 성별 설정
                setTestGender()
            }
        }
    }

    private fun loadCoupleInfo() {
        viewModelScope.launch {
            try {
                android.util.Log.d("HomeViewModel", "Couple 정보 로딩 시작...")
                val response = momProfileRepository.getCoupleDetailInfo()

                if (response.isSuccessful) {
                    val coupleDetail = response.body()
                    if (coupleDetail != null) {
                        val coupleId = coupleDetail.couple.coupleId
                        val menstrualDate = coupleDetail.couple.menstrualDate

                        android.util.Log.d("HomeViewModel", "Couple 정보 로딩 성공: coupleId=$coupleId, menstrualDate=$menstrualDate")

                        _coupleId.value = coupleId

                        if (menstrualDate.isNullOrEmpty()) {
                            android.util.Log.w("HomeViewModel", "서버에서 받은 menstrualDate가 null 또는 빈 문자열입니다")
                            _menstrualDate.value = null
                        } else {
                            _menstrualDate.value = menstrualDate
                        }

                        // 현재 임신 일수 계산 (네겔레 법칙)
                        calculateCurrentPregnancyDay(menstrualDate)

                    } else {
                        android.util.Log.w("HomeViewModel", "Couple 정보 응답이 null입니다")
                    }
                } else {
                    android.util.Log.e("HomeViewModel", "Couple 정보 로딩 실패: ${response.code()} - ${response.message()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Couple 정보 로딩 중 에러 발생", e)
                e.printStackTrace()
            }
        }
    }

    private fun calculateCurrentPregnancyDay(menstrualDate: String?) {
        try {
            if (menstrualDate.isNullOrEmpty()) {
                return
            }

            val lmpDate = LocalDate.parse(menstrualDate)
            val today = LocalDate.now()

            // 네겔레 법칙: 마지막 생리 첫날부터 현재까지의 날짜 차이
            val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(lmpDate, today).toInt()
            val pregnancyDays = daysBetween  // 날짜 차이만 사용

            _currentPregnancyDay.value = pregnancyDays.coerceAtLeast(1)


        } catch (e: Exception) {
            _currentPregnancyDay.value = 1
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
                } else {

                }
            } catch (e: Exception) {

                e.printStackTrace()
            }
        }
    }

    fun refreshProfile() {
        viewModelScope.launch {
            try {
                // 사용자 정보와 커플 정보를 모두 다시 로드
                loadUserGender() // 이 안에서 loadCoupleInfo()도 호출됨
                loadMomProfile()
            } catch (e: Exception) {

                e.printStackTrace()
            }
        }
    }

    fun forceRefreshProfile() {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                // 1. 사용자 정보 먼저 로드
                loadUserGender()

                // 2. 프로필 정보 로드 (커플 정보가 로드된 후)
                loadMomProfile()

            } catch (e: Exception) {

                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun refreshMomProfileSilently() {
        viewModelScope.launch {
            try {
                // 로딩 상태를 변경하지 않고 백그라운드에서 새로고침
                val profile = momProfileRepository.getHomeProfileData()
                if (profile != null) {
                    _momProfile.value = profile

                }
                val currentMenstrualDate = _menstrualDate.value
                if (currentMenstrualDate != null) {
                    calculateCurrentPregnancyDay(currentMenstrualDate)
                }

            } catch (e: Exception) {

                e.printStackTrace()
            }
        }
    }

    fun refreshCalendarEvents() {
        loadCurrentMonthEvents()
    }
}