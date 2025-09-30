package com.ms.helloworld.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.ms.helloworld.dto.request.CoupleUpdateRequest
import com.ms.helloworld.dto.request.MemberUpdateRequest
import com.ms.helloworld.dto.response.MomProfile
import com.ms.helloworld.dto.response.MemberProfile
import com.ms.helloworld.dto.response.CoupleInviteCodeResponse
import com.ms.helloworld.dto.response.CoupleProfile
import com.ms.helloworld.dto.response.CoupleDetailResponse
import com.ms.helloworld.dto.response.UserDetail
import com.ms.helloworld.repository.AuthRepository
import com.ms.helloworld.repository.MomProfileRepository
import com.ms.helloworld.repository.CoupleRepository
import com.ms.helloworld.repository.FcmRepository
import com.ms.helloworld.util.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject

data class CoupleProfileState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val momProfile: MomProfile? = null,
    val memberProfile: MemberProfile? = null,
    val coupleProfile: CoupleProfile? = null,
    val inviteCode: String? = null,
    val inviteCodeResponse: CoupleInviteCodeResponse? = null,
    val isPartnerConnected: Boolean = false,
    val coupleDetail: CoupleDetailResponse? = null,
    val userA: UserDetail? = null,
    val userB: UserDetail? = null,
)

@HiltViewModel
class CoupleProfileViewModel @Inject constructor(
    private val momProfileRepository: MomProfileRepository,
    private val coupleRepository: CoupleRepository,
    private val tokenManager: TokenManager,
    private val fcmRepository: FcmRepository,
    private val authRepository: AuthRepository
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

                // 새로운 CoupleDetail API 사용
                val momProfile = momProfileRepository.getHomeProfileData()

                // CoupleDetail API 직접 호출하여 상세 정보 가져오기
                val response = momProfileRepository.getCoupleDetailInfo()

                if (response.isSuccessful) {
                    val coupleDetail = response.body()
                    if (coupleDetail != null) {

                        // 파트너 연결 여부 확인
                        val isPartnerConnected = coupleDetail.couple.userAId != null &&
                                               coupleDetail.couple.userBId != null

                        // 현재 사용자의 실제 정보를 getUserInfo로 가져오기
                        val userInfoResponse = momProfileRepository.getUserInfo()
                        val currentUserProfile = userInfoResponse.member


                        // 기존 형태로 변환하여 호환성 유지 (실제 사용자 정보 사용)
                        val memberProfile = currentUserProfile

                        _state.value = _state.value.copy(
                            isLoading = false,
                            momProfile = momProfile,
                            memberProfile = memberProfile,
                            coupleProfile = coupleDetail.couple,
                            isPartnerConnected = isPartnerConnected,
                            // 새로운 데이터
                            coupleDetail = coupleDetail,
                            userA = coupleDetail.userA,
                            userB = coupleDetail.userB
                        )
                    } else {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            errorMessage = "커플 상세 정보를 불러오는데 실패했습니다."
                        )
                    }
                } else {
                    // 404일 경우 기본 사용자 정보만으로 처리
                    if (response.code() == 404) {
                        val userInfo = momProfileRepository.getUserInfo()
                        val memberProfile = userInfo.member

                        _state.value = _state.value.copy(
                            isLoading = false,
                            memberProfile = memberProfile,
                            isPartnerConnected = false
                        )
                    } else {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            errorMessage = "서버 오류가 발생했습니다: ${response.code()}"
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

    fun refreshProfile() {
        loadCoupleProfile()
    }

    fun updateProfile(nickname: String, age: Int?, menstrualDate: LocalDate?, dueDate: LocalDate?, isChildbirth: Boolean?) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                // 1. 멤버 정보 업데이트 (닉네임, 나이)
                val memberUpdateRequest = MemberUpdateRequest(
                    nickname = nickname,
                    age = age
                )
                val memberUpdateResult = momProfileRepository.updateProfile(memberUpdateRequest)

                // 2. 커플 정보 업데이트 (출산예정일, 생리일자, 출산경험 등)
                var coupleUpdateResult: Any? = true // 기본값은 성공으로 설정

                // 커플 정보가 하나라도 있으면 업데이트 수행
                if (dueDate != null || menstrualDate != null || isChildbirth != null) {
                    var calculatedWeek: Int? = null

                    if (dueDate != null) {
                        // 출산예정일로부터 현재 임신주차 계산 (LMP + (day - 1) 방식과 일관성 유지)
                        val today = LocalDate.now()
                        val daysDifference = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate)
                        val totalPregnancyDays = 280 // 40주 * 7일
                        val currentPregnancyDays = totalPregnancyDays - daysDifference
                        calculatedWeek = ((currentPregnancyDays - 1) / 7 + 1).toInt().coerceIn(1, 42)
                    }

                    val coupleUpdateRequest = CoupleUpdateRequest(
                        pregnancyWeek = calculatedWeek,
                        due_date = dueDate?.toString(),
                        menstrual_date = menstrualDate?.toString(),
                        is_childbirth = isChildbirth
                    )

                    coupleUpdateResult = momProfileRepository.updateCoupleInfo(coupleUpdateRequest)
                } else {

                }

                if (memberUpdateResult != null && coupleUpdateResult != null) {

                    // 성공 시 프로필 정보 다시 로드
                    loadCoupleProfile()
                } else {

                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "프로필 업데이트에 실패했습니다."
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


    fun generateInviteCode() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val result = coupleRepository.generateInviteCode()
                if (result.isSuccess) {
                    val inviteCodeResponse = result.getOrNull()
                    _state.value = _state.value.copy(
                        isLoading = false,
                        inviteCodeResponse = inviteCodeResponse,
                        inviteCode = inviteCodeResponse?.code
                    )
                } else {
                    val error = result.exceptionOrNull()?.message ?: "초대 코드 생성 실패"
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = error
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "네트워크 오류"
                )
            }
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                // 1. 서버에 로그아웃 요청
                try {
                    val logoutSuccess = authRepository.logout()
                    if (logoutSuccess) {
                    } else {
                    }
                } catch (e: Exception) {
                }

                // 2. FCM 토큰 해제 (서버에서)
                try {
                    val fcmSuccess = fcmRepository.unregisterToken()
                    if (fcmSuccess) {

                    } else {

                    }
                } catch (e: Exception) {

                }

                // 3. 로컬 토큰 삭제
                tokenManager.clearTokens()


                // 4. WearOS 토큰 제거
                removeTokenFromWearOS(context)

                // 5. 상태 초기화
                _state.value = CoupleProfileState()


            } catch (e: Exception) {

                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "로그아웃 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }

    private suspend fun removeTokenFromWearOS(context: Context) {
        try {
            val dataClient = Wearable.getDataClient(context)
            val putDataMapRequest = PutDataMapRequest.create("/jwt_token").apply {
                dataMap.putString("access_token", "")
                dataMap.putString("refresh_token", "")
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }
            val putDataRequest = putDataMapRequest.asPutDataRequest()
            putDataRequest.setUrgent()
            dataClient.putDataItem(putDataRequest).await()

        } catch (e: Exception) {

        }
    }

    fun acceptInviteCode(code: String) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val result = coupleRepository.acceptInvite(code)
                if (result.isSuccess) {

                    // 프로필 정보 다시 로드
                    loadCoupleProfile()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "초대 코드 수락 실패"
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = error
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "네트워크 오류"
                )
            }
        }
    }

    fun disconnectCouple() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val result = coupleRepository.disconnectCouple()
                if (result.isSuccess) {

                    // 프로필 정보 다시 로드
                    loadCoupleProfile()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "커플 연결 해제 실패"
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = error
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "네트워크 오류"
                )
            }
        }
    }
}