package com.ms.helloworld.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ms.helloworld.dto.request.UserInfoRequest
import com.ms.helloworld.repository.OnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val nickname: String = "",
    val selectedGender: String = "엄마",
    val age: String = "",
    val isFirstPregnancy: Boolean? = false,
    val pregnancyCount: String = "",
    val lastMenstrualDate: String = "",
    val menstrualCycle: String = "",
    val isFormValid: Boolean = false,
    val isLoading: Boolean = false,
    val submitSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingRepository: OnboardingRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun updateNickname(nickname: String) {
        _state.value = _state.value.copy(nickname = nickname)
        validateForm()
    }

    fun updateGender(gender: String) {
        _state.value = _state.value.copy(selectedGender = gender)
        validateForm()
    }

    fun updateAge(age: String) {
        _state.value = _state.value.copy(age = age)
        validateForm()
    }

    fun updatePregnancyExperience(hasExperience: Boolean) {
        _state.value = _state.value.copy(isFirstPregnancy = hasExperience)
        validateForm()
    }

    fun updatePregnancyCount(count: String) {
        _state.value = _state.value.copy(pregnancyCount = count)
        validateForm()
    }

    fun updateLastMenstrualDate(date: String) {
        _state.value = _state.value.copy(lastMenstrualDate = date)
        validateForm()
    }

    fun updateMenstrualCycle(cycle: String) {
        _state.value = _state.value.copy(menstrualCycle = cycle)
        validateForm()
    }

    private fun validateForm() {
        val currentState = _state.value
        val isValid = currentState.nickname.isNotBlank() &&
                currentState.age.isNotBlank() &&
                currentState.age.toIntOrNull() != null &&
                currentState.isFirstPregnancy != null &&
                currentState.lastMenstrualDate.isNotBlank() &&
                currentState.menstrualCycle.isNotBlank() &&
                currentState.menstrualCycle.toIntOrNull() != null

        _state.value = currentState.copy(isFormValid = isValid)
    }

    fun submitUserInfo() {
        if (!_state.value.isFormValid) return

        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val request = UserInfoRequest(
                    nickname = _state.value.nickname,
                    gender = _state.value.selectedGender,
                    age = _state.value.age.toInt(),
                    isFirstPregnancy = _state.value.isFirstPregnancy ?: true,
                    pregnancyCount = if (_state.value.pregnancyCount.isNotBlank())
                        _state.value.pregnancyCount.toIntOrNull() else null,
                    lastMenstrualDate = _state.value.lastMenstrualDate,
                    menstrualCycle = _state.value.menstrualCycle.toInt()
                )

                val result = onboardingRepository.submitUserInfo(request)

                if (result.isSuccess) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        submitSuccess = true
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "알 수 없는 오류가 발생했습니다."
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