package com.ms.helloworld.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ms.helloworld.dto.request.MemberRegisterRequest
import com.ms.helloworld.dto.request.MemberUpdateRequest
import com.ms.helloworld.dto.request.CoupleUpdateRequest
import com.ms.helloworld.dto.request.CoupleCreateRequest
import com.ms.helloworld.repository.MomProfileRepository
import com.ms.helloworld.repository.CoupleRepository
import com.ms.helloworld.model.OnboardingStatus
import com.ms.helloworld.model.OnboardingCheckResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class OnboardingState(
    val nickname: String = "",
    val selectedGender: String = "", // "엄마" or "아빠"
    val age: String = "",
    val menstrualDate: String = "", // yyyy-MM-dd format
    val menstrualCycle: String = "", // 생리 주기 (일수)
    val isChildbirth: Boolean? = null, // nullable로 변경
    val calculatedPregnancyWeek: Int = 0, // 계산된 임신 주차
    val dueDate: String = "", // yyyy-MM-dd format
    val invitationCode: String = "", // 아빠용 초대 코드
    val isInviteCodeValid: Boolean = false, // 초대 코드 검증 상태
    val isValidatingInviteCode: Boolean = false, // 초대 코드 검증 중
    val inviteCodeError: String? = null, // 초대 코드 에러 메시지
    val isFormValid: Boolean = false,
    val isLoading: Boolean = false,
    val submitSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val momProfileRepository: MomProfileRepository,
    private val coupleRepository: CoupleRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun updateNickname(nickname: String) {
        _state.value = _state.value.copy(nickname = nickname)
        validateForm()
    }

    fun updateGender(gender: String) {
        // 성별 변경 시 다른 필드들 초기화 (성별에 따라 다른 필드가 나타나므로)
        _state.value = _state.value.copy(
            selectedGender = gender,
            nickname = "",
            age = "",
            menstrualDate = "",
            menstrualCycle = "",
            isChildbirth = null,
            calculatedPregnancyWeek = 0,
            dueDate = "",
            invitationCode = ""
        )
        validateForm()
    }

    fun updateAge(age: String) {
        _state.value = _state.value.copy(age = age)
        validateForm()
    }

    fun updateMenstrualDate(date: String) {
        _state.value = _state.value.copy(menstrualDate = date)
        calculatePregnancyWeek()
        validateForm()
    }

    fun updateMenstrualCycle(cycle: String) {
        _state.value = _state.value.copy(menstrualCycle = cycle)
        calculatePregnancyWeek()
        validateForm()
    }

    fun updateChildbirthStatus(isChildbirth: Boolean?) {
        _state.value = _state.value.copy(isChildbirth = isChildbirth)
        validateForm()
    }

    private fun calculatePregnancyWeek() {
        val currentState = _state.value

        if (currentState.menstrualDate.isNotBlank() && currentState.menstrualCycle.isNotBlank()) {
            try {
                val menstrualDate = LocalDate.parse(currentState.menstrualDate, DateTimeFormatter.ISO_LOCAL_DATE)
                val cycleLength = currentState.menstrualCycle.toIntOrNull() ?: 28

                // 네겔레 법칙: 마지막 생리일부터 현재까지의 날짜 차이로 임신 주차 계산
                val today = LocalDate.now()
                val daysSinceLastPeriod = ChronoUnit.DAYS.between(menstrualDate, today)
                val pregnancyWeek = ((daysSinceLastPeriod / 7) + 1).toInt()

                // 음수가 되지 않도록 보정 (1~42주 범위)
                val calculatedWeek = when {
                    pregnancyWeek < 1 -> 1
                    pregnancyWeek > 42 -> 42
                    else -> pregnancyWeek
                }

                // 예정일 계산 (마지막 생리일 + 280일 = 40주)
                val dueDate = menstrualDate.plusDays(280)
                val dueDateString = dueDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

                println("📅 임신 정보 계산 (네겔레 법칙):")
                println("  - 마지막 생리일: $menstrualDate")
                println("  - 오늘: $today")
                println("  - 경과 일수: ${daysSinceLastPeriod}일")
                println("  - 계산된 예정일: $dueDate (생리일 + 280일)")
                println("  - 임신 주차: ${calculatedWeek}주 (${daysSinceLastPeriod}일 ÷ 7 + 1)")

                _state.value = _state.value.copy(
                    calculatedPregnancyWeek = calculatedWeek,
                    dueDate = dueDateString
                )
            } catch (e: Exception) {
                // 날짜 파싱 실패 시 0으로 설정
                _state.value = _state.value.copy(calculatedPregnancyWeek = 0)
            }
        }
    }

    fun updateDueDate(date: String) {
        _state.value = _state.value.copy(dueDate = date)
        validateForm()
    }

    fun updateInvitationCode(code: String) {
        _state.value = _state.value.copy(
            invitationCode = code,
            isInviteCodeValid = false, // 코드 변경시 검증 상태 초기화
            inviteCodeError = null
        )
        validateForm()
    }

    fun validateInviteCode() {
        if (_state.value.invitationCode.isBlank()) return

        viewModelScope.launch {
            try {
                // 현재 사용자 정보 확인
                println("🔍 OnboardingViewModel - 초대 코드 검증 전 사용자 정보 확인")
                try {
                    val userInfo = momProfileRepository.getUserInfo()
                    println("👤 현재 사용자 정보:")
                    println("  - ID: ${userInfo.member.id}")
                    println("  - 성별: ${userInfo.member.gender}")
                    println("  - 닉네임: ${userInfo.member.nickname}")
                    println("  - 현재 커플 상태: ${if (userInfo.couple != null) "커플 있음" else "커플 없음"}")
                    if (userInfo.couple != null) {
                        println("  - 커플 ID: ${userInfo.couple?.coupleId}")
                        println("  - userAId: ${userInfo.couple?.userAId}")
                        println("  - userBId: ${userInfo.couple?.userBId}")
                    }
                } catch (e: Exception) {
                    println("❌ 사용자 정보 조회 실패: ${e.message}")
                }

                _state.value = _state.value.copy(
                    isValidatingInviteCode = true,
                    inviteCodeError = null
                )

                val result = coupleRepository.acceptInvite(_state.value.invitationCode)
                if (result.isSuccess) {
                    _state.value = _state.value.copy(
                        isValidatingInviteCode = false,
                        isInviteCodeValid = true,
                        inviteCodeError = null
                    )
                    println("✅ 초대 코드 검증 성공")
                } else {
                    _state.value = _state.value.copy(
                        isValidatingInviteCode = false,
                        isInviteCodeValid = false,
                        inviteCodeError = "유효하지 않은 초대 코드입니다."
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isValidatingInviteCode = false,
                    isInviteCodeValid = false,
                    inviteCodeError = e.message ?: "초대 코드 검증 중 오류가 발생했습니다."
                )
            }
            validateForm()
        }
    }

    private fun validateForm() {
        val currentState = _state.value
        val isValid = currentState.nickname.isNotBlank() &&
                currentState.age.isNotBlank() &&
                currentState.age.toIntOrNull() != null &&
                currentState.menstrualDate.isNotBlank()

        _state.value = currentState.copy(isFormValid = isValid)
    }

    suspend fun saveBasicInfo(): Boolean {
        val currentState = _state.value

        // 기본 정보 유효성 검사
        if (currentState.nickname.isBlank() ||
            currentState.age.isBlank() ||
            currentState.selectedGender.isBlank()) {
            return false
        }

        return try {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)

            val combinedNickname = "${currentState.nickname} ${currentState.selectedGender}"
            val gender = if (currentState.selectedGender == "엄마") "female" else "male"

            val request = MemberRegisterRequest(
                nickname = combinedNickname,
                gender = gender,
                age = currentState.age.toInt()
            )

            println("💾 기본 정보 저장:")
            println("  - nickname: ${request.nickname}")
            println("  - gender: ${request.gender}")
            println("  - age: ${request.age}")

            val result = momProfileRepository.registerUser(request)
            if (result != null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = null
                )
                println("✅ 기본 정보 저장 성공")
                true
            } else {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "기본 정보 저장에 실패했습니다."
                )
                false
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoading = false,
                errorMessage = e.message ?: "네트워크 오류가 발생했습니다."
            )
            false
        }
    }

    suspend fun saveCoupleInfo(): Boolean {
        val currentState = _state.value

        // 엄마인 경우에만 couple 정보 저장
        if (currentState.selectedGender != "엄마") {
            return true // 아빠는 couple 정보 저장하지 않음
        }

        return try {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)

            // Couple 정보 저장 (생리일자, 출산경험, 임신주차, 예정일)
            val coupleUpdateRequest = CoupleUpdateRequest(
                pregnancyWeek = if (currentState.calculatedPregnancyWeek > 0) currentState.calculatedPregnancyWeek else null,
                due_date = if (currentState.dueDate.isNotBlank()) currentState.dueDate else null,
                menstrual_date = if (currentState.menstrualDate.isNotBlank()) currentState.menstrualDate else null,
                is_childbirth = currentState.isChildbirth
            )

            println("💾 커플 정보 저장:")
            println("  - pregnancyWeek: ${coupleUpdateRequest.pregnancyWeek}")
            println("  - due_date: ${coupleUpdateRequest.due_date}")
            println("  - menstrual_date: ${coupleUpdateRequest.menstrual_date}")
            println("  - is_childbirth: ${coupleUpdateRequest.is_childbirth}")

            val result = momProfileRepository.updateCoupleInfo(coupleUpdateRequest)
            if (result != null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = null
                )
                println("✅ 커플 정보 저장 성공")
                true
            } else {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "커플 정보 저장에 실패했습니다."
                )
                false
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoading = false,
                errorMessage = e.message ?: "네트워크 오류가 발생했습니다."
            )
            false
        }
    }

    fun completeOnboarding() {
        val currentState = _state.value

        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                when (currentState.selectedGender) {
                    "엄마" -> {
                        // 엄마: 커플 테이블 생성 및 정보 저장
                        println("👩 엄마 커플 생성 시작")

                        val coupleCreateRequest = CoupleCreateRequest(
                            pregnancyWeek = if (currentState.calculatedPregnancyWeek > 0) currentState.calculatedPregnancyWeek else null,
                            due_date = if (currentState.dueDate.isNotBlank()) currentState.dueDate else null,
                            menstrual_date = if (currentState.menstrualDate.isNotBlank()) currentState.menstrualDate else null,
                            menstrual_cycle = if (currentState.menstrualCycle.isNotBlank()) currentState.menstrualCycle.toIntOrNull() else null,
                            is_childbirth = currentState.isChildbirth
                        )

                        println("💾 백엔드로 전송할 couple 데이터:")
                        println("  - pregnancyWeek: ${coupleCreateRequest.pregnancyWeek}")
                        println("  - due_date: ${coupleCreateRequest.due_date}")
                        println("  - menstrual_date: ${coupleCreateRequest.menstrual_date}")
                        println("  - menstrual_cycle: ${coupleCreateRequest.menstrual_cycle}")
                        println("  - is_childbirth: ${coupleCreateRequest.is_childbirth}")

                        val result = momProfileRepository.createCouple(coupleCreateRequest)
                        if (result != null) {
                            _state.value = _state.value.copy(
                                isLoading = false,
                                submitSuccess = true
                            )
                            println("✅ 엄마 커플 생성 완료")
                        } else {
                            _state.value = _state.value.copy(
                                isLoading = false,
                                errorMessage = "커플 생성에 실패했습니다."
                            )
                        }
                    }
                    "아빠" -> {
                        // 아빠: 초대코드 검증만 확인 (기본 정보는 이미 저장됨)
                        println("👨 아빠 온보딩 완료 - 초대코드로 couple 연결 완료")

                        if (currentState.isInviteCodeValid) {
                            _state.value = _state.value.copy(
                                isLoading = false,
                                submitSuccess = true
                            )
                            println("✅ 아빠 온보딩 완료")
                        } else {
                            _state.value = _state.value.copy(
                                isLoading = false,
                                errorMessage = "초대코드 검증이 완료되지 않았습니다."
                            )
                        }
                    }
                    else -> {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            errorMessage = "잘못된 성별 정보입니다."
                        )
                    }
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

    suspend fun checkAndResumeOnboarding(): OnboardingCheckResult {
        return try {
            println("🔍 온보딩 상태 체크 시작")
            val result = momProfileRepository.checkOnboardingStatus()

            when (result.status) {
                OnboardingStatus.BASIC_COMPLETED -> {
                    // 기존 사용자 정보로 상태 초기화
                    initializeFromExistingData(result)
                    println("📝 기존 정보로 상태 초기화 완료")
                }
                OnboardingStatus.FULLY_COMPLETED -> {
                    println("✅ 온보딩 이미 완료됨")
                }
                OnboardingStatus.NOT_STARTED -> {
                    println("🆕 새로운 사용자 - 처음부터 시작")
                }
            }

            result
        } catch (e: Exception) {
            println("❌ 온보딩 상태 체크 실패: ${e.message}")

            // 네트워크 오류인 경우 예외를 다시 던져서 로그인 화면으로 이동하도록 함
            if (e is java.net.UnknownHostException ||
                e.message?.contains("Unable to resolve host") == true ||
                e.message?.contains("Network") == true) {
                println("🌐 네트워크 오류 감지 → 로그인 화면으로 리다이렉트")
                throw e
            }

            // 다른 오류는 새로운 사용자로 간주
            OnboardingCheckResult(OnboardingStatus.NOT_STARTED)
        }
    }

    private suspend fun initializeFromExistingData(result: OnboardingCheckResult) {
        try {
            // 사용자 정보 가져오기
            val userInfo = momProfileRepository.getUserInfo()
            val member = userInfo.member
            val couple = userInfo.couple

            // 기본 정보 초기화
            val genderText = if (member.gender?.lowercase() == "female") "엄마" else "아빠"
            val nickname = member.nickname?.replace(" $genderText", "") ?: ""

            _state.value = _state.value.copy(
                nickname = nickname,
                selectedGender = genderText,
                age = member.age?.toString() ?: "",
                // couple 정보가 있다면 초기화
                menstrualDate = couple?.menstrualDate ?: "",
                isChildbirth = couple?.isChildbirth,
                dueDate = couple?.dueDate ?: ""
            )

            println("📋 기존 데이터로 상태 초기화:")
            println("  - nickname: $nickname")
            println("  - gender: $genderText")
            println("  - age: ${member.age}")

        } catch (e: Exception) {
            println("❌ 기존 데이터 초기화 실패: ${e.message}")
        }
    }

    fun getResumePageIndex(result: OnboardingCheckResult): Int {
        return when {
            result.shouldGoToMomForm -> {
                // 엄마 정보 입력 페이지로 (기본 온보딩 화면들을 건너뛰고 MOM_INFO_FORM으로)
                6 // 일반적으로 온보딩 화면 5개 + 기본 정보 1개 = 6번째가 MOM_INFO_FORM
            }
            result.shouldGoToDadForm -> {
                // 아빠 정보 입력 페이지로
                6 // DAD_INFO_FORM도 같은 위치
            }
            else -> 0 // 처음부터 시작
        }
    }
}