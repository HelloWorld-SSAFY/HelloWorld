package com.ms.helloworld.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ms.helloworld.dto.request.MemberRegisterRequest
import com.ms.helloworld.dto.request.MemberUpdateRequest
import com.ms.helloworld.dto.request.CoupleUpdateRequest
import com.ms.helloworld.repository.MomProfileRepository
import com.ms.helloworld.repository.CoupleRepository
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

                // 배란일 계산 (생리 시작일 + 생리주기 - 14일)
                val ovulationDate = menstrualDate.plusDays((cycleLength - 14).toLong())

                // 임신 주차 계산 (배란일부터 현재까지의 일수 / 7 + 2주)
                val today = LocalDate.now()
                val daysSinceOvulation = ChronoUnit.DAYS.between(ovulationDate, today)
                val pregnancyWeek = ((daysSinceOvulation / 7) + 2).toInt()

                // 음수가 되지 않도록 보정
                val calculatedWeek = if (pregnancyWeek > 0) pregnancyWeek else 0

                _state.value = _state.value.copy(calculatedPregnancyWeek = calculatedWeek)
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
                        println("  - 커플 ID: ${userInfo.couple?.id}")
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

    fun completeOnboarding() {
        val currentState = _state.value

        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                when (currentState.selectedGender) {
                    "엄마" -> {
                        // 엄마: 임신 정보 업데이트 (기본 정보는 이미 저장됨)
                        println("👩 엄마 임신 정보 업데이트")

                        // Member 정보 업데이트 (생리일, 출산경험) - updateProfile API 사용
                        val memberUpdateRequest = MemberUpdateRequest(
                            nickname = null, // 닉네임은 이미 저장되었으므로 null
                            age = null, // 나이도 이미 저장되었으므로 null
                            menstrual_date = if (currentState.menstrualDate.isNotBlank()) currentState.menstrualDate else null
                        )

                        val memberResult = momProfileRepository.updateProfile(memberUpdateRequest)

                        // Couple 정보 업데이트 (임신주차, 예정일) - updateCoupleInfo API 사용
                        val coupleUpdateRequest = CoupleUpdateRequest(
                            pregnancyWeek = if (currentState.calculatedPregnancyWeek > 0) currentState.calculatedPregnancyWeek else null,
                            due_date = if (currentState.dueDate.isNotBlank()) currentState.dueDate else null
                        )

                        val coupleResult = momProfileRepository.updateCoupleInfo(coupleUpdateRequest)

                        if (memberResult != null && coupleResult != null) {
                            _state.value = _state.value.copy(
                                isLoading = false,
                                submitSuccess = true
                            )
                            println("✅ 엄마 정보 업데이트 완료")
                        } else {
                            _state.value = _state.value.copy(
                                isLoading = false,
                                errorMessage = "임신 정보 저장에 실패했습니다."
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
}