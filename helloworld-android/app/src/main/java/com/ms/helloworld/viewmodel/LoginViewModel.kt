package com.ms.helloworld.viewmodel

import android.content.Context
import android.util.Log
import com.ms.helloworld.R
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.ms.helloworld.dto.request.GoogleLoginRequest
import com.ms.helloworld.repository.AuthRepository
import com.ms.helloworld.util.TokenManager
import com.ms.helloworld.repository.MomProfileRepository
import com.ms.helloworld.model.OnboardingStatus
import androidx.navigation.NavHostController
import com.ms.helloworld.dto.request.Platforms
import com.ms.helloworld.navigation.Screen
import com.ms.helloworld.repository.FcmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class LoginState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val errorMessage: String? = null,
    val loginSuccess: Boolean = false,
    val userGender: String? = null,
    val isAutoLoginChecked: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
    private val momProfileRepository: MomProfileRepository,
    private val fcmRepository: FcmRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    companion object {
        private const val TAG = "LoginViewModel"
        private const val TOKEN_PATH = "/jwt_token"
        private const val ACCESS_TOKEN_KEY = "access_token"
        private const val REFRESH_TOKEN_KEY = "refresh_token"
        private const val TIMESTAMP_KEY = "timestamp"
        private const val FCM_PREFS = "fcm_prefs"
        private const val FCM_TOKEN_KEY = "fcm_token"
    }

    fun signInWithGoogle(context: Context) {
        // 이미 로그인된 상태면 중복 실행 방지
        if (_state.value.isLoggedIn) {
            Log.d(TAG, "이미 로그인된 상태입니다.")
            return
        }

        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val credentialManager = CredentialManager.create(context)

                // strings.xml에서 Google Client ID 가져오기
                val googleClientId = context.getString(R.string.google_client_id)

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId(googleClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    request = request,
                    context = context
                )

                val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
                val idToken = credential.idToken

                // Spring Boot 서버로 소셜 로그인 요청
                val loginRequest = GoogleLoginRequest(
                    idToken = idToken
                )

                val loginResponse = authRepository.socialLogin(loginRequest)

                if (loginResponse != null) {
                    Log.d(TAG, "Member ID: ${loginResponse.memberId}")
                    Log.d(TAG, "Access token: ${loginResponse.accessToken}")
                    Log.d(TAG, "Refresh token: ${loginResponse.refreshToken}")
                    Log.d(TAG, "User gender: ${loginResponse.gender}")

                    // JWT 토큰 저장
                    tokenManager.saveTokens(
                        accessToken = loginResponse.accessToken,
                        refreshToken = loginResponse.refreshToken
                    )
                    Log.d(TAG, "저장된 accessToken 확인: ${tokenManager.getAccessToken()}")
                    Log.d(TAG, "저장된 refreshToken 확인: ${tokenManager.getRefreshToken()}")

                    launch {
                        sendTokenToWearOS(context, loginResponse.accessToken, loginResponse.refreshToken)
                    }
                    launch {
                        registerStoredFcmToken(context)
                    }

//                    // WearOS로 토큰 전송
//                    sendTokenToWearOS(context, loginResponse.accessToken, loginResponse.refreshToken)
//
//                    // 로그인 성공 후 저장된 FCM 토큰 등록
//                    registerStoredFcmToken(context)


                    _state.value = _state.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        loginSuccess = true,
                        userGender = loginResponse.gender,
                        isAutoLoginChecked = true
                    )
                    Log.d(TAG, "소셜 로그인 성공")
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "로그인 실패. 잠시 후 다시 시도해주세요."
                    )
                    Log.d(TAG, "소셜 로그인 실패")
                }

            } catch (e: GetCredentialException) {
                Log.e(TAG, "소셜 로그인 실패", e)

                val errorMessage = when {
                    e.javaClass.simpleName.contains("NoCredentialException") ||
                    e.message?.contains("No credentials available") == true ->
                        "Google 계정을 찾을 수 없습니다. 디바이스 설정에서 Google 계정을 추가하거나 Google Play 서비스를 업데이트해주세요."
                    e.javaClass.simpleName.contains("GetCredentialCancellationException") ||
                    e.message?.contains("cancelled") == true ->
                        "Google 로그인이 취소되었습니다."
                    e.message?.contains("SIGN_IN_REQUIRED") == true ->
                        "Google 로그인이 필요합니다. 다시 시도해주세요."
                    else ->
                        "Google 로그인에 실패했습니다: ${e.message ?: "알 수 없는 오류"}"
                }
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = errorMessage
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "로그인 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }

    /**
     * 로그인 성공 후 저장된 FCM 토큰을 서버에 등록
     */
    private fun registerStoredFcmToken(context: Context) {
        viewModelScope.launch {
            try {
                val sharedPrefs = context.getSharedPreferences(FCM_PREFS, Context.MODE_PRIVATE)
                val storedToken = sharedPrefs.getString(FCM_TOKEN_KEY, null)

                if (storedToken != null) {
                    Log.d(TAG, "저장된 FCM 토큰 발견, 서버 등록 시도")
                    fcmRepository.registerTokenAsync(
                        token = storedToken,
                        platform = Platforms.ANDROID
                    )
                } else {
                    Log.d(TAG, "저장된 FCM 토큰 없음")
                    // 현재 FCM 토큰을 새로 가져와서 등록
                    registerCurrentFcmToken()
                }
            } catch (e: Exception) {
                Log.w(TAG, "FCM 토큰 등록 실패: ${e.message}")
            }
        }
    }

    /**
     * 현재 FCM 토큰을 가져와서 등록
     */
    private fun registerCurrentFcmToken() {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                viewModelScope.launch {
                    try {
                        Log.d(TAG, "현재 FCM 토큰 등록 시도: $token")
                        fcmRepository.registerTokenAsync(
                            token = token,
                            platform = com.ms.helloworld.dto.request.Platforms.ANDROID
                        )
                        Log.d(TAG, "현재 FCM 토큰 등록 완료")
                    } catch (e: Exception) {
                        Log.w(TAG, "현재 FCM 토큰 등록 실패: ${e.message}")
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "FCM 토큰 가져오기 실패: ${e.message}")
            }
    }

    private suspend fun sendTokenToWearOS(context: Context, accessToken: String, refreshToken: String) {
        try {
            val dataClient = Wearable.getDataClient(context)
            val nodeClient = Wearable.getNodeClient(context)

            // 연결된 노드 확인
            val nodes = nodeClient.connectedNodes.await()

            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected WearOS devices found")
                return
            }

            val putDataMapRequest = PutDataMapRequest.create(TOKEN_PATH).apply {
                dataMap.putString(ACCESS_TOKEN_KEY, accessToken)
                dataMap.putString(REFRESH_TOKEN_KEY, refreshToken)
                dataMap.putLong(TIMESTAMP_KEY, System.currentTimeMillis()) // 동기화를 위한 타임스탬프
            }

            val putDataRequest: PutDataRequest = putDataMapRequest.asPutDataRequest()
            putDataRequest.setUrgent() // 즉시 전송

            val result = dataClient.putDataItem(putDataRequest).await()
            Log.d(TAG, "JWT tokens sent to WearOS successfully - URI: ${result.uri}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send tokens to WearOS", e)
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun clearLoginSuccess() {
        _state.value = _state.value.copy(loginSuccess = false)
    }

    // 수동 로그인 성공 후 처리
    fun handleLoginSuccess(navController: NavHostController) {
        // 이미 처리된 경우 중복 실행 방지
        if (!_state.value.loginSuccess) {
            return
        }

        viewModelScope.launch {
            try {
                clearLoginSuccess()
                val result = momProfileRepository.checkOnboardingStatus()

                navigateBasedOnOnboardingStatus(result, navController)

            } catch (e: Exception) {
                navController.navigate(Screen.OnboardingScreens.route) {
                    popUpTo(Screen.LoginScreen.route) { inclusive = true }
                }
            }
        }
    }

    // 온보딩 상태에 따른 화면 이동
    private fun navigateBasedOnOnboardingStatus(
        result: com.ms.helloworld.model.OnboardingCheckResult,
        navController: NavHostController
    ) {
        when (result.status) {
            OnboardingStatus.FULLY_COMPLETED -> {
                navController.navigate(Screen.HomeScreen.route) {
                    popUpTo(Screen.LoginScreen.route) { inclusive = true }
                }
            }
            OnboardingStatus.BASIC_COMPLETED -> {
                navController.navigate(Screen.OnboardingScreens.route) {
                    popUpTo(Screen.LoginScreen.route) { inclusive = true }
                }
            }
            OnboardingStatus.NOT_STARTED -> {
                navController.navigate(Screen.OnboardingScreens.route) {
                    popUpTo(Screen.LoginScreen.route) { inclusive = true }
                }
            }
        }
    }

}