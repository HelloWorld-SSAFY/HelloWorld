package com.ms.helloworld.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ms.helloworld.dto.request.MemberRegisterRequest
import com.ms.helloworld.repository.MomProfileRepository
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
    val isFormValid: Boolean = false,
    val isLoading: Boolean = false,
    val submitSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val momProfileRepository: MomProfileRepository
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
        _state.value = _state.value.copy(invitationCode = code)
        validateForm()
    }

    private fun validateForm() {
        val currentState = _state.value
        val isValid = currentState.nickname.isNotBlank() &&
                currentState.age.isNotBlank() &&
                currentState.age.toIntOrNull() != null &&
                currentState.menstrualDate.isNotBlank()

        _state.value = currentState.copy(isFormValid = isValid)
    }

    fun submitUserInfo() {
        val currentState = _state.value

        // 성별별 유효성 검사
        val isValid = when (currentState.selectedGender) {
            "엄마" -> {
                currentState.nickname.isNotBlank() &&
                currentState.age.isNotBlank() &&
                currentState.selectedGender.isNotBlank() &&
                currentState.isChildbirth != null &&
                currentState.menstrualDate.isNotBlank() &&
                currentState.menstrualCycle.isNotBlank()
            }
            "아빠" -> {
                currentState.nickname.isNotBlank() &&
                currentState.age.isNotBlank() &&
                currentState.selectedGender.isNotBlank() &&
                currentState.invitationCode.isNotBlank()
            }
            else -> false
        }

        if (!isValid) return

        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val combinedNickname = when (_state.value.selectedGender) {
                    "엄마", "아빠" -> "${_state.value.nickname} ${_state.value.selectedGender}"
                    else -> _state.value.nickname
                }

                val request = MemberRegisterRequest(
                    nickname = combinedNickname,
                    gender = when (_state.value.selectedGender) {
                        "엄마" -> "female"
                        "아빠" -> "male"
                        else -> _state.value.selectedGender
                    },
                    age = _state.value.age.toInt(),
                    menstrual_date = if (_state.value.selectedGender == "엄마" && _state.value.menstrualDate.isNotBlank())
                        _state.value.menstrualDate else null,
                    is_childbirth = if (_state.value.selectedGender == "엄마") _state.value.isChildbirth else null,
                    pregnancyWeek = if (_state.value.selectedGender == "엄마" && _state.value.calculatedPregnancyWeek > 0)
                        _state.value.calculatedPregnancyWeek else null,
                    due_date = if (_state.value.selectedGender == "엄마" && _state.value.dueDate.isNotBlank())
                        _state.value.dueDate else null,
                    invitationCode = if (_state.value.selectedGender == "아빠" && _state.value.invitationCode.isNotBlank())
                        _state.value.invitationCode else null
                )

                val result = momProfileRepository.registerUser(request)

                if (result != null) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        submitSuccess = true
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "등록에 실패했습니다."
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
}