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
// Kakao 관련 import 임시 주석 처리
// import com.kakao.sdk.auth.model.OAuthToken
// import com.kakao.sdk.common.model.ClientError
// import com.kakao.sdk.common.model.ClientErrorCause
// import com.kakao.sdk.user.UserApiClient
import com.ms.helloworld.dto.request.SocialLoginRequest
import com.ms.helloworld.dto.request.GoogleLoginRequest
import com.ms.helloworld.dto.response.LoginResponse
import com.ms.helloworld.repository.AuthRepository
import com.ms.helloworld.util.TokenManager
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
    val userGender: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    companion object {
        private const val TAG = "LoginViewModel"
        private const val TOKEN_PATH = "/jwt_token"
        private const val ACCESS_TOKEN_KEY = "access_token"
        private const val REFRESH_TOKEN_KEY = "refresh_token"
        private const val TIMESTAMP_KEY = "timestamp"
    }

    fun signInWithGoogle(context: Context) {
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

                Log.d(TAG, "Google ID Token received (full): $idToken")
                Log.d(TAG, "Google ID Token length: ${idToken.length}")

                // Spring Boot 서버로 소셜 로그인 요청
                val loginRequest = GoogleLoginRequest(
                    idToken = idToken
                )

                Log.d(TAG, "Sending login request to server - Google")
                Log.d(TAG, "Token length being sent: ${loginRequest.idToken.length}")

                val loginResponse = authRepository.socialLogin(loginRequest)

                Log.d(TAG, "Server response received: $loginResponse")
                if (loginResponse != null) {
                    Log.d(TAG, "Member ID: ${loginResponse.memberId}")
                    Log.d(TAG, "Access token: ${loginResponse.accessToken}")
                    Log.d(TAG, "Refresh token: ${loginResponse.refreshToken}")
                    Log.d(TAG, "User gender: ${loginResponse.gender}")
                    Log.d(TAG, "Gender is null: ${loginResponse.gender == null}")
                    Log.d(TAG, "Access token is null: ${loginResponse.accessToken == null}")
                    Log.d(TAG, "Refresh token is null: ${loginResponse.refreshToken == null}")
                    // JWT 토큰 저장
                    tokenManager.saveTokens(
                        accessToken = loginResponse.accessToken,
                        refreshToken = loginResponse.refreshToken
                    )

                    // WearOS로 토큰 전송
                    sendTokenToWearOS(context, loginResponse.accessToken, loginResponse.refreshToken)


                    _state.value = _state.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        loginSuccess = true,
                        userGender = loginResponse.gender
                    )
                    Log.d(TAG, "Google login successful")
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "서버 로그인에 실패했습니다."
                    )
                }

            } catch (e: GetCredentialException) {
                Log.e(TAG, "Google login failed", e)
                Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
                Log.e(TAG, "Exception message: ${e.message}")

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
                Log.e(TAG, "Login error", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "로그인 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }

    // 로그아웃 기능 추가
    fun signOut(context: Context) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting logout process...")

                // 로컬 토큰 삭제
                tokenManager.clearTokens()

                // WearOS에서 토큰 제거
                removeTokenFromWearOS(context)

                // 상태 초기화
                _state.value = LoginState()

                Log.d(TAG, "Logout completed successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Logout error", e)
                _state.value = _state.value.copy(
                    errorMessage = "로그아웃 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }

    private suspend fun sendTokenToWearOS(context: Context, accessToken: String, refreshToken: String) {
        try {
            Log.d(TAG, "Attempting to send tokens to WearOS...")
            val dataClient = Wearable.getDataClient(context)
            val nodeClient = Wearable.getNodeClient(context)

            // 연결된 노드 확인
            val nodes = nodeClient.connectedNodes.await()
            Log.d(TAG, "Connected nodes: ${nodes.size}")
            for (node in nodes) {
                Log.d(TAG, "Node: ${node.displayName} - ${node.id}")
            }

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

    private suspend fun removeTokenFromWearOS(context: Context) {
        try {
            Log.d(TAG, "Removing tokens from WearOS...")
            val dataClient = Wearable.getDataClient(context)
            val nodeClient = Wearable.getNodeClient(context)

            // 연결된 노드 확인
            val nodes = nodeClient.connectedNodes.await()
            Log.d(TAG, "Connected nodes for token removal: ${nodes.size}")

            val putDataMapRequest = PutDataMapRequest.create(TOKEN_PATH).apply {
                dataMap.putString(ACCESS_TOKEN_KEY, "")
                dataMap.putString(REFRESH_TOKEN_KEY, "")
                dataMap.putLong(TIMESTAMP_KEY, System.currentTimeMillis())
            }

            val putDataRequest: PutDataRequest = putDataMapRequest.asPutDataRequest()
            putDataRequest.setUrgent()

            val result = dataClient.putDataItem(putDataRequest).await()
            Log.d(TAG, "Tokens removed from WearOS successfully - URI: ${result.uri}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove tokens from WearOS", e)
        }
    }

    fun signInWithKakao(context: Context) {
        // Kakao 로그인 임시 비활성화
        _state.value = _state.value.copy(
            isLoading = false,
            errorMessage = "카카오 로그인은 현재 준비 중입니다."
        )

        /*
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                // 카카오톡 설치 여부 확인
                if (UserApiClient.instance.isKakaoTalkLoginAvailable(context)) {
                    // 카카오톡으로 로그인
                    loginWithKakaoTalk(context)
                } else {
                    // 카카오 계정으로 로그인
                    loginWithKakaoAccount(context)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Kakao login error", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "카카오 로그인 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
        */
    }

    /*
    // Kakao 관련 메서드들 임시 주석 처리
    private fun loginWithKakaoTalk(context: Context) {
        UserApiClient.instance.loginWithKakaoTalk(context) { token, error ->
            if (error != null) {
                Log.e(TAG, "카카오톡 로그인 실패", error)

                // 사용자가 카카오톡 설치 후 디바이스 권한 요청 화면에서 로그인을 취소한 경우
                if (error is ClientError && error.reason == ClientErrorCause.Cancelled) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "카카오 로그인이 취소되었습니다."
                    )
                } else {
                    // 카카오톡에 연결된 카카오계정이 없는 경우, 카카오계정으로 로그인 시도
                    loginWithKakaoAccount(context)
                }
            } else if (token != null) {
                Log.d(TAG, "카카오톡 로그인 성공: ${token.accessToken.take(20)}...")
                handleKakaoLoginSuccess(token)
            }
        }
    }

    private fun loginWithKakaoAccount(context: Context) {
        UserApiClient.instance.loginWithKakaoAccount(context) { token, error ->
            if (error != null) {
                Log.e(TAG, "카카오 계정 로그인 실패", error)
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "카카오 로그인에 실패했습니다: ${error.message}"
                )
            } else if (token != null) {
                Log.d(TAG, "카카오 계정 로그인 성공: ${token.accessToken.take(20)}...")
                handleKakaoLoginSuccess(token)
            }
        }
    }

    private fun handleKakaoLoginSuccess(token: OAuthToken) {
        viewModelScope.launch {
            try {
                // Spring Boot 서버로 소셜 로그인 요청
                val loginRequest = SocialLoginRequest(
                    provider = "kakao",
                    token = token.accessToken
                )

                val loginResponse = authRepository.socialLogin(loginRequest)

                if (loginResponse != null) {
                    // JWT 토큰 저장
                    tokenManager.saveTokens(
                        accessToken = loginResponse.accessToken,
                        refreshToken = loginResponse.refreshToken
                    )

                    _state.value = _state.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        loginSuccess = true
                    )
                    Log.d(TAG, "Kakao login successful")
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "서버 로그인에 실패했습니다."
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Kakao server login error", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "서버 연동 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }
    */

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun clearLoginSuccess() {
        _state.value = _state.value.copy(loginSuccess = false)
    }


}