package com.ms.helloworld.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ms.helloworld.dto.response.MomProfile
import com.ms.helloworld.repository.MomProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CoupleProfileState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val momProfile: MomProfile? = null,
    val inviteCode: String? = null
)

@HiltViewModel
class CoupleProfileViewModel @Inject constructor(
    private val momProfileRepository: MomProfileRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CoupleProfileState())
    val state: StateFlow<CoupleProfileState> = _state.asStateFlow()

    init {
        loadCoupleProfile()
    }

    private fun loadCoupleProfile() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val momProfile = momProfileRepository.getMomProfile()

                if (momProfile != null) {
                    // TODO: 초대코드 기능은 백엔드에서 구현 필요 (여성유저에게만 노출)
                    val inviteCode = "ABC123" // 임시 코드

                    _state.value = _state.value.copy(
                        isLoading = false,
                        momProfile = momProfile,
                        inviteCode = inviteCode
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "프로필 정보를 불러오는데 실패했습니다."
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

    fun refreshProfile() {
        loadCoupleProfile()
    }
}