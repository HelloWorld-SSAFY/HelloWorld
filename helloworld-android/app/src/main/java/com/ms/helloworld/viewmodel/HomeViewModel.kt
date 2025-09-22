package com.ms.helloworld.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ms.helloworld.dto.response.CalendarEventResponse
import com.ms.helloworld.dto.response.MomProfile
import com.ms.helloworld.dto.response.MemberProfile
import com.ms.helloworld.repository.CalendarRepository
import com.ms.helloworld.repository.MomProfileRepository
import com.ms.helloworld.util.TokenManager
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
    private val tokenManager: TokenManager
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

    private val _menstrualDate = MutableStateFlow<String?>(null)
    val menstrualDate: StateFlow<String?> = _menstrualDate.asStateFlow()

    private val _currentPregnancyDay = MutableStateFlow<Int>(1)
    val currentPregnancyDay: StateFlow<Int> = _currentPregnancyDay.asStateFlow()

    // 임시 테스트용 - API 호출이 실패할 경우 기본값 설정
    private fun setTestGender() {
        println("🧪 HomeViewModel($viewModelId) - 테스트용 성별 설정: FEMALE")
        _userGender.value = "FEMALE" // 임시로 여성으로 설정
    }

    private fun getCoupleId(): Long? {
        return tokenManager.getCoupleId()
    }

    init {
        // 순차적 초기화: 사용자 정보 → 커플 정보 → 기타 데이터
        initializeSequentially()
    }

    private fun initializeSequentially() {
        viewModelScope.launch {
            try {
                println("🚀 HomeViewModel($viewModelId) - 순차적 초기화 시작")

                // 1단계: 기본 사용자 정보 로드
                loadUserGender()

                // 2단계: 커플 정보 로드 (사용자 정보 로드 완료 후)
                // loadUserGender()에서 loadCoupleInfo()가 호출됨

                // 3단계: 기타 데이터 로드 (병렬로 실행 가능)
                loadMomProfile()
                loadCurrentMonthEvents()

                println("🚀 HomeViewModel($viewModelId) - 순차적 초기화 완료")
            } catch (e: Exception) {
                println("💥 HomeViewModel($viewModelId) - 초기화 실패: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    private fun loadMomProfile() {
        viewModelScope.launch {
            try {
                println("🚀 HomeViewModel($viewModelId) - loadHomeProfile 시작")
                _isLoading.value = true
                val profile = momProfileRepository.getHomeProfileData()
                if (profile != null) {
                    println("🚀 HomeViewModel($viewModelId) - Couple 테이블 기반 데이터: 주차=${profile.pregnancyWeek}, 닉네임=${profile.nickname}")
                    println("🚀 HomeViewModel($viewModelId) - 예정일=${profile.dueDate}, D-day=${profile.daysUntilDue}")

                    // StateFlow 강제 업데이트 - 새로운 객체로 교체
                    val newProfile = profile.copy()
                    _momProfile.value = newProfile

                    println("🚀 HomeViewModel($viewModelId) - _momProfile.value 업데이트 완료: ${_momProfile.value.pregnancyWeek}주차")
                    println("🚀 HomeViewModel($viewModelId) - StateFlow 현재값: ${momProfile.value.pregnancyWeek}주차")
                } else {
                    println("❌ HomeViewModel($viewModelId) - Couple 데이터에서 null 받음")
                }
            } catch (e: Exception) {
                println("💥 HomeViewModel - loadHomeProfile 예외: ${e.message}")
                e.printStackTrace()
            } finally {
                _isLoading.value = false
                println("🏁 HomeViewModel - loadHomeProfile 완료")
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

                val gender = userInfo.member.gender
                val userId = userInfo.member.id

                println("🚻 HomeViewModel($viewModelId) - 원본 성별: $gender")
                println("🚻 HomeViewModel($viewModelId) - 사용자 ID: $userId")

                _userGender.value = gender
                _userId.value = userId

                // 커플 정보는 별도 API에서 가져오기
                loadCoupleInfo()

                println("🚻 HomeViewModel($viewModelId) - 기본 사용자 정보 저장 완료")
            } catch (e: Exception) {
                println("💥 HomeViewModel - loadUserGender 예외: ${e.message}")
                e.printStackTrace()
                // API 호출 실패 시 임시로 테스트 성별 설정
                setTestGender()
            }
        }
    }

    private fun loadCoupleInfo() {
        viewModelScope.launch {
            try {
                println("🚀 HomeViewModel($viewModelId) - loadCoupleInfo 시작")
                val response = momProfileRepository.getCoupleDetailInfo()

                if (response.isSuccessful) {
                    val coupleDetail = response.body()
                    if (coupleDetail != null) {
                        val coupleId = coupleDetail.couple.coupleId
                        val menstrualDate = coupleDetail.couple.menstrualDate

                        println("🚻 HomeViewModel($viewModelId) - 커플 상세 정보:")
                        println("  - 커플 ID: $coupleId")
                        println("  - 생리일: $menstrualDate")

                        _coupleId.value = coupleId
                        _menstrualDate.value = menstrualDate

                        // 현재 임신 일수 계산 (네겔레 법칙)
                        calculateCurrentPregnancyDay(menstrualDate)

                        println("🚻 HomeViewModel($viewModelId) - 커플 정보 저장 완료")
                    } else {
                        println("❌ HomeViewModel($viewModelId) - 커플 상세 정보가 null")
                    }
                } else {
                    println("❌ HomeViewModel($viewModelId) - 커플 상세 API 실패: ${response.code()}")
                }
            } catch (e: Exception) {
                println("💥 HomeViewModel - loadCoupleInfo 예외: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun calculateCurrentPregnancyDay(menstrualDate: String?) {
        try {
            if (menstrualDate.isNullOrEmpty()) {
                println("❌ HomeViewModel($viewModelId) - 생리일이 null이므로 임신 일수 계산 건너뜀")
                return
            }

            val lmpDate = LocalDate.parse(menstrualDate)
            val today = LocalDate.now()

            // 네겔레 법칙: 마지막 생리 첫날부터 현재까지의 날짜 차이
            val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(lmpDate, today).toInt()
            val pregnancyDays = daysBetween  // 날짜 차이만 사용

            _currentPregnancyDay.value = pregnancyDays.coerceAtLeast(1)

            println("🧮 HomeViewModel($viewModelId) - 임신 일수 계산 (네겔레 법칙):")
            println("  - 마지막 생리일: $lmpDate")
            println("  - 오늘: $today")
            println("  - 날짜 차이: ${daysBetween}일")
            println("  - 임신 일수: ${pregnancyDays}일차")
            println("  - 임신 주수: ${pregnancyDays / 7.0}주 → ${(pregnancyDays / 7) + 1}주차")

        } catch (e: Exception) {
            println("💥 HomeViewModel($viewModelId) - 임신 일수 계산 실패: ${e.message}")
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

                val coupleId = getCoupleId()
                if (coupleId == null) {
                    println("❌ HomeViewModel($viewModelId) - coupleId가 토큰에서 추출되지 않음")
                    return@launch
                }

                val result = calendarRepository.getEvents(
                    coupleId = coupleId,
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
                println("🔄 HomeViewModel - refreshHomeProfileSilently 시작")
                // 로딩 상태를 변경하지 않고 백그라운드에서 새로고침
                val profile = momProfileRepository.getHomeProfileData()
                if (profile != null) {
                    println("🔄 HomeViewModel - 새 Couple 기반 프로필 데이터: 주차=${profile.pregnancyWeek}, 닉네임=${profile.nickname}")
                    println("🔄 HomeViewModel - 예정일=${profile.dueDate}, D-day=${profile.daysUntilDue}")
                    _momProfile.value = profile
                    println("🔄 HomeViewModel - _momProfile 상태 업데이트 완료")
                } else {
                    println("❌ HomeViewModel - Couple 기반 프로필 데이터가 null입니다")
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