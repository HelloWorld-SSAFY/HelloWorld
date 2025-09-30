package com.ms.helloworld.repository

import android.util.Log
import com.ms.helloworld.dto.request.MemberRegisterRequest
import com.ms.helloworld.dto.request.MemberUpdateRequest
import com.ms.helloworld.dto.request.CoupleUpdateRequest
import com.ms.helloworld.dto.request.CoupleCreateRequest
import com.ms.helloworld.dto.request.AvatarUrlRequest
import com.ms.helloworld.dto.response.MemberProfileResponse
import com.ms.helloworld.dto.response.MemberRegisterResponse
import com.ms.helloworld.dto.response.AvatarUrlResponse
import com.ms.helloworld.dto.response.MomProfile
import com.ms.helloworld.dto.response.MemberProfile
import com.ms.helloworld.dto.response.CoupleDetailResponse
import com.ms.helloworld.model.OnboardingStatus
import com.ms.helloworld.model.OnboardingCheckResult
import com.ms.helloworld.network.api.UserApi
import com.ms.helloworld.util.TokenManager
import retrofit2.Response
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MomProfileRepository @Inject constructor(
    private val userApi: UserApi,
    private val tokenManager: TokenManager
) {

    companion object {
        private const val TAG = "MomProfileRepository"
    }

    suspend fun getUserInfo(): MemberProfileResponse {
        return userApi.getUserInfo()
    }

    suspend fun getCoupleDetailInfo(): Response<CoupleDetailResponse> {
        Log.d("MomProfileRepository", "getCoupleDetailInfo() 호출 시작")

        return try {
            val response = userApi.getCoupleDetail()

            Log.d("MomProfileRepository", "getCoupleDetailInfo API 호출 완료: ${response.code()}")
//            Log.d("MomProfileRepository", "- Is successful: ${response.isSuccessful}")
//            Log.d("MomProfileRepository", "- Headers: ${response.headers()}")
//            Log.d("MomProfileRepository", "- Raw body exists: ${response.raw().body != null}")

            if (response.isSuccessful) {
                Log.d("MomProfileRepository", "Body Data ${response.body()}")
            } else {
                val errorBody = response.errorBody()?.string()
                Log.d("MomProfileRepository", "- Error body: '$errorBody'")
            }

            response

        } catch (e: Exception) {
            Log.e("MomProfileRepository", "getCoupleDetailInfo() 예외 발생: ${e.javaClass.simpleName}: ${e.message}", e)
            throw e
        }
    }

    suspend fun getMomProfile(): MomProfile? {
        return try {
            Log.d(TAG, "Making API call to getUserInfo")
            val response = userApi.getUserInfo()
            Log.d(TAG, "API response received: $response")

            // couple 정보 상세 분석
            Log.d(TAG, "=== COUPLE ANALYSIS ===")
            val couple = response.couple
            if (couple != null) {
                Log.d(TAG, "Couple is not null")
                Log.d(TAG, "Couple id: ${couple.coupleId}")
                Log.d(TAG, "Couple userAId: ${couple.userAId}")
                Log.d(TAG, "Couple userBId: ${couple.userBId}")
                Log.d(TAG, "Couple pregnancyWeek: ${couple.pregnancyWeek}")
                Log.d(TAG, "Couple dueDate: ${couple.dueDate}")
            } else {
                Log.d(TAG, "Couple is completely null!")
            }
            Log.d(TAG, "=== END COUPLE ANALYSIS ===")

            val momProfile = convertToMomProfile(response)
            Log.d(TAG, "Converted MomProfile: $momProfile")

            momProfile
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching mom profile", e)
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")

            if (e is retrofit2.HttpException) {
                try {
                    val errorBody = e.response()?.errorBody()?.string()
                    Log.e(TAG, "HTTP Error Code: ${e.code()}")
                    Log.e(TAG, "HTTP Error Body: $errorBody")
                } catch (ioException: Exception) {
                    Log.e(TAG, "Failed to read error body: ${ioException.message}")
                }
            }
            null
        }
    }

    suspend fun registerUser(request: MemberRegisterRequest): MemberRegisterResponse? {
        return try {
            Log.d(TAG, "👤 사용자 등록 API 호출 시작")
            Log.d(TAG, "Request 전체: $request")
            Log.d(TAG, "Request nickname: ${request.nickname}")
            Log.d(TAG, "Request gender: ${request.gender}")
            Log.d(TAG, "Request age: ${request.age}")

            val response = userApi.registerUser(request)
            Log.d(TAG, "사용자 등록 API 응답 성공: $response")
            response
        } catch (e: Exception) {
            Log.e(TAG, "사용자 등록 API 실패", e)
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")

            if (e is retrofit2.HttpException) {
                try {
                    val errorBody = e.response()?.errorBody()?.string()
                    Log.e(TAG, "HTTP Error Code: ${e.code()}")
                    Log.e(TAG, "HTTP Error Body: $errorBody")
                } catch (ioException: Exception) {
                    Log.e(TAG, "Failed to read error body: ${ioException.message}")
                }
            }
            null
        }
    }

    suspend fun updateProfile(request: MemberUpdateRequest): MemberRegisterResponse? {
        return try {
            userApi.updateProfile(request)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createCouple(request: CoupleCreateRequest): MemberRegisterResponse? {
        return try {
            Log.d(TAG, "💑 커플 생성 API 호출 시작")
            Log.d(TAG, "Request: $request")
            Log.d(TAG, "Request due_date: ${request.due_date}")
            Log.d(TAG, "Request pregnancyWeek: ${request.pregnancyWeek}")
            Log.d(TAG, "Request menstrual_date: ${request.menstrual_date}")
            Log.d(TAG, "Request is_childbirth: ${request.is_childbirth}")

            val response = userApi.createCouple(request)
            Log.d(TAG, "✅ 커플 생성 API 응답 성공: $response")
            response
        } catch (e: Exception) {
            Log.e(TAG, "❌ 커플 생성 API 실패", e)
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")

            if (e is retrofit2.HttpException) {
                try {
                    val errorBody = e.response()?.errorBody()?.string()
                    Log.e(TAG, "HTTP Error Code: ${e.code()}")
                    Log.e(TAG, "HTTP Error Body: $errorBody")
                } catch (ioException: Exception) {
                    Log.e(TAG, "Failed to read error body: ${ioException.message}")
                }
            }
            null
        }
    }

    suspend fun updateCoupleInfo(request: CoupleUpdateRequest): MemberRegisterResponse? {
        return try {
            Log.d(TAG, "💑 커플 정보 업데이트 API 호출 시작")
            Log.d(TAG, "Request: $request")
            Log.d(TAG, "Request due_date: ${request.due_date}")
            Log.d(TAG, "Request pregnancyWeek: ${request.pregnancyWeek}")

            val response = userApi.updateCoupleInfo(request)
            Log.d(TAG, "✅ 커플 정보 업데이트 API 응답 성공: $response")
            response
        } catch (e: Exception) {
            Log.e(TAG, "❌ 커플 정보 업데이트 API 실패", e)
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")

            if (e is retrofit2.HttpException) {
                try {
                    val errorBody = e.response()?.errorBody()?.string()
                    Log.e(TAG, "HTTP Error Code: ${e.code()}")
                    Log.e(TAG, "HTTP Error Body: $errorBody")
                } catch (ioException: Exception) {
                    Log.e(TAG, "Failed to read error body: ${ioException.message}")
                }
            }
            null
        }
    }

    suspend fun updateProfileImage(url: String): AvatarUrlResponse? {
        return try {
            userApi.updateProfileImage(AvatarUrlRequest(url))
        } catch (e: Exception) {
            null
        }
    }

    private fun convertToMomProfile(response: MemberProfileResponse): MomProfile? {
        Log.d(TAG, "Converting response to MomProfile")
        Log.d(TAG, "Response member: ${response.member}")
        Log.d(TAG, "Response couple: ${response.couple}")

        val couple = response.couple
        val member = response.member

        Log.d(TAG, "Couple pregnancyWeek: ${couple?.pregnancyWeek}")
        Log.d(TAG, "Couple dueDate: ${couple?.dueDate}")

        return if (couple != null) {
            Log.d(TAG, "Using couple data (may be partial)")
            Log.d(TAG, "Couple userAId: ${couple.userAId}")
            Log.d(TAG, "Couple userBId: ${couple.userBId}")
            Log.d(TAG, "Couple pregnancyWeek: ${couple.pregnancyWeek}")
            Log.d(TAG, "Couple dueDate: ${couple.dueDate}")
            Log.d(TAG, "Couple menstrualDate: ${couple.menstrualDate}")

            // member 정보를 사용하여 닉네임 결정 (현재 로그인한 사용자)
            val nickname = member?.nickname ?: "엄마"
            Log.d(TAG, "Using member nickname: $nickname")

            // couple 데이터가 있으면 사용, 없으면 계산이나 기본값 사용
            val pregnancyWeek = couple.pregnancyWeek ?: run {
                // pregnancyWeek가 없으면 menstrual_date로 계산
                if (couple.menstrualDate != null) {
                    val (_, calculatedWeek) = calculatePregnancyInfo(couple.menstrualDate)
                    calculatedWeek
                } else {
                    1 // 기본값
                }
            }

            val dueDate = if (couple.dueDate != null) {
                LocalDate.parse(couple.dueDate, DateTimeFormatter.ISO_LOCAL_DATE)
            } else if (couple.menstrualDate != null) {
                // dueDate가 없으면 menstrual_date로 계산
                val (calculatedDueDate, _) = calculatePregnancyInfo(couple.menstrualDate)
                calculatedDueDate
            } else {
                LocalDate.now().plusDays(280) // 기본값
            }

            val lastMenstruationDate = couple.menstrualDate?.let {
                try {
                    LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse menstrual date: $it", e)
                    null
                }
            }

            val momProfile = MomProfile(
                nickname = nickname,
                pregnancyWeek = pregnancyWeek,
                dueDate = dueDate,
                lastMenstruationDate = lastMenstruationDate
            )
            Log.d(TAG, "Created MomProfile with couple data: $momProfile")
            momProfile
        } else if (member != null) {
            // couple 정보가 없거나 불완전하면 member 정보로 계산
            Log.d(TAG, "Couple data incomplete, calculating from member data")
            Log.d(TAG, "Member nickname: ${member.nickname}")
            Log.d(TAG, "Member gender: ${member.gender}")
            val nickname = member.nickname ?: "엄마"

            // couple 테이블의 생리일자 정보를 사용하여 계산
            val (calculatedDueDate, calculatedWeek) = calculatePregnancyInfo(couple?.menstrualDate)

            val momProfile = MomProfile(
                nickname = nickname,
                pregnancyWeek = calculatedWeek,
                dueDate = calculatedDueDate
            )
            Log.d(TAG, "Created MomProfile with calculated data: $momProfile")
            momProfile
        } else {
            Log.d(TAG, "Both couple and member data are incomplete, returning null")
            null
        }
    }

    suspend fun getHomeProfileData(): MomProfile? {
        return try {

            val response = userApi.getCoupleDetail()
            if (!response.isSuccessful) {
                Log.e(TAG, "getHomeProfileData API 실패: ${response.code()}")
                return null
            }

            val coupleDetail = response.body()
            if (coupleDetail == null) {
                Log.e(TAG, "getHomeProfileData 응답이 null")
                return null
            }

            val couple = coupleDetail.couple
            val userA = coupleDetail.userA

            Log.d(TAG, "CoupleDetail 조회 성공")
            Log.d(TAG, "user_a 닉네임: ${userA.nickname}")
            Log.d(TAG, "couple 임신주차: ${couple.pregnancyWeek}")

            // user_a의 닉네임 사용 (항상 존재)
            val userANickname = userA.nickname ?: "엄마"

            val pregnancyWeek = couple.pregnancyWeek ?: run {
                if (couple.menstrualDate != null) {
                    val (_, calculatedWeek) = calculatePregnancyInfo(couple.menstrualDate)
                    calculatedWeek
                } else {
                    1
                }
            }

            val dueDate = if (couple.dueDate != null) {
                LocalDate.parse(couple.dueDate, DateTimeFormatter.ISO_LOCAL_DATE)
            } else if (couple.menstrualDate != null) {
                val (calculatedDueDate, _) = calculatePregnancyInfo(couple.menstrualDate)
                calculatedDueDate
            } else {
                LocalDate.now().plusDays(280)
            }

            val lastMenstruationDate = couple.menstrualDate?.let {
                try {
                    LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse menstrual date: $it", e)
                    null
                }
            }

            val homeProfile = MomProfile(
                nickname = userANickname,
                pregnancyWeek = pregnancyWeek,
                dueDate = dueDate,
                lastMenstruationDate = lastMenstruationDate
            )

            homeProfile
        } catch (e: Exception) {
            Log.e(TAG, "HomeProfile 조회 실패", e)
            null
        }
    }

    suspend fun checkOnboardingStatus(): OnboardingCheckResult {
        return try {
            Log.d(TAG, "🔍 온보딩 상태 체크 시작 - 새로운 CoupleDetail API 사용")

            // 네트워크 연결 테스트
            try {
                val testHost = java.net.InetAddress.getByName("j13d204.p.ssafy.io")
            } catch (e: Exception) {
                Log.e(TAG, "checkOnboardingStatus 실패: ${e.message}")

                // Google DNS로 테스트
                try {
                    val googleDns = java.net.InetAddress.getByName("8.8.8.8")
                } catch (e2: Exception) {
                    Log.e(TAG, "인터넷 연결 자체에 문제: ${e2.message}")
                }
            }

            // 현재 사용자 ID를 토큰에서 가져오기
            val currentUserId = tokenManager.getUserId()?.toLongOrNull()
            if (currentUserId == null) {
                Log.e(TAG, "토큰에서 사용자 ID 추출 실패")
                return OnboardingCheckResult(OnboardingStatus.NOT_STARTED)
            }
            Log.d(TAG, "토큰에서 추출한 현재 사용자 ID: $currentUserId")

            // 토큰 유효성 먼저 체크 (기존 API로)
            try {
                Log.d(TAG, "토큰 유효성 체크 중...")
                val userInfoTest = userApi.getUserInfo()
                Log.d(TAG, "토큰 유효 - 기본 사용자 정보 조회 성공")
            } catch (e: Exception) {
                Log.e(TAG, "토큰 무효 - 기본 사용자 정보 조회 실패: ${e.message}")
                if (e is retrofit2.HttpException && (e.code() == 401 || e.code() == 403)) {
                    Log.d(TAG, "토큰 만료로 추정 - 토큰 삭제 후 로그인 필요")
//                    tokenManager.clearTokens()
                }
                return OnboardingCheckResult(OnboardingStatus.NOT_STARTED)
            }

            val response = userApi.getCoupleDetail()
            if (!response.isSuccessful) {
                when (response.code()) {
                    404 -> {
                        Log.d(TAG, "📭 커플 정보 없음 (404) - 기본 사용자 정보로 체크")
                        // 기본 사용자 정보만으로 처리
                        val userInfo = userApi.getUserInfo()
                        val member = userInfo.member

                        if (member.nickname.isNullOrBlank()) {
                            return OnboardingCheckResult(OnboardingStatus.NOT_STARTED)
                        }

                        val gender = member.gender?.lowercase()
                        return when (gender) {
                            "female" -> OnboardingCheckResult(
                                status = OnboardingStatus.BASIC_COMPLETED,
                                userGender = gender,
                                shouldGoToMomForm = true
                            )
                            "male" -> OnboardingCheckResult(
                                status = OnboardingStatus.BASIC_COMPLETED,
                                userGender = gender,
                                shouldGoToDadForm = true
                            )
                            else -> OnboardingCheckResult(
                                status = OnboardingStatus.BASIC_COMPLETED,
                                userGender = gender
                            )
                        }
                    }
                    500 -> {
                        Log.w(TAG, "⚠️ CoupleDetail API 500 오류 - 백엔드 구현 문제로 추정")
                        Log.w(TAG, "📝 임시로 NOT_STARTED 처리 (백엔드 API 구현 대기)")
                        return OnboardingCheckResult(OnboardingStatus.NOT_STARTED)
                    }
                    else -> {
                        Log.e(TAG, "❌ CoupleDetail API 실패: ${response.code()}")
                        return OnboardingCheckResult(OnboardingStatus.NOT_STARTED)
                    }
                }
            }

            val coupleDetail = response.body()
            if (coupleDetail == null) {
                Log.e(TAG, "CoupleDetail 응답이 null")
                return OnboardingCheckResult(OnboardingStatus.NOT_STARTED)
            }

            val couple = coupleDetail.couple
            val userA = coupleDetail.userA
            val userB = coupleDetail.userB

            // 현재 사용자가 userA인지 userB인지 판별
            val currentUser = when (currentUserId) {
                userA.id -> userA
                userB?.id -> userB
                else -> {
                    Log.e(TAG, "❌ 현재 사용자 ID($currentUserId)가 userA(${userA.id}) 또는 userB(${userB?.id})와 일치하지 않음")
                    return OnboardingCheckResult(OnboardingStatus.NOT_STARTED)
                }
            }

            Log.d(TAG, "=== 온보딩 상태 체크 상세 정보 ===")
            Log.d(TAG, "Current user: $currentUser")
            Log.d(TAG, "Current user ID: ${currentUser.id}")
            Log.d(TAG, "Current user nickname: ${currentUser.nickname}")
            Log.d(TAG, "Current user gender: ${currentUser.gender}")
            Log.d(TAG, "Couple info: $couple")
            Log.d(TAG, "Couple ID: ${couple.coupleId}")
            Log.d(TAG, "Couple userAId: ${couple.userAId}")
            Log.d(TAG, "Couple userBId: ${couple.userBId}")
            Log.d(TAG, "=================================")

            // member 정보가 없으면 온보딩 시작 안함
            if (currentUser.nickname.isNullOrBlank()) {
                Log.d(TAG, "❌ Member 정보 없음 - NOT_STARTED")
                return OnboardingCheckResult(OnboardingStatus.NOT_STARTED)
            }

            val gender = currentUser.gender?.lowercase()
            Log.d(TAG, "=== 성별 및 분기 로직 ===")
            Log.d(TAG, "Original gender: ${currentUser.gender}")
            Log.d(TAG, "Lowercase gender: $gender")
            Log.d(TAG, "Gender comparison - female: ${gender == "female"}")
            Log.d(TAG, "Gender comparison - male: ${gender == "male"}")
            Log.d(TAG, "==========================")

            when (gender) {
                "female" -> {
                    // 여성: couple 테이블이 생성되어 있고 데이터가 있으면 완료
                    Log.d(TAG, "👩 여성 사용자 - couple 테이블 있음 → FULLY_COMPLETED")
                    OnboardingCheckResult(
                        status = OnboardingStatus.FULLY_COMPLETED,
                        userGender = gender
                    )
                }
                "male" -> {
                    // 남성: couple 테이블이 있고 userBId가 본인 ID와 일치해야 함
                    if (couple.userBId == null) {
                        Log.d(TAG, "👨 남성 사용자 - couple 테이블 있지만 userBId 없음 → DAD_FORM으로")
                        OnboardingCheckResult(
                            status = OnboardingStatus.BASIC_COMPLETED,
                            userGender = gender,
                            shouldGoToDadForm = true
                        )
                    } else {
                        // userBId가 있으면 이미 연결된 것으로 간주
                        Log.d(TAG, "👨 남성 사용자 - couple에 연결됨(userBId: ${couple.userBId}) → FULLY_COMPLETED")
                        OnboardingCheckResult(
                            status = OnboardingStatus.FULLY_COMPLETED,
                            userGender = gender
                        )
                    }
                }
                else -> {
                    Log.d(TAG, "❓ 알 수 없는 성별 - BASIC_COMPLETED")
                    OnboardingCheckResult(
                        status = OnboardingStatus.BASIC_COMPLETED,
                        userGender = gender
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 온보딩 상태 체크 실패", e)
            // API 호출 실패 시 온보딩 시작 안한 것으로 간주
            OnboardingCheckResult(OnboardingStatus.NOT_STARTED)
        }
    }

    private fun calculatePregnancyInfo(menstrualDateString: String?): Pair<LocalDate, Int> {
        if (menstrualDateString != null) {
            try {
                val menstrualDate = LocalDate.parse(menstrualDateString, DateTimeFormatter.ISO_LOCAL_DATE)
                Log.d(TAG, "Parsed menstrual date: $menstrualDate")

                // 예정일 = 마지막 생리일 + 280일 (40주)
                val dueDate = menstrualDate.plusDays(280)
                Log.d(TAG, "Calculated due date: $dueDate")

                // 현재 임신 주차 계산
                val today = LocalDate.now()
                val daysSinceLastPeriod = java.time.temporal.ChronoUnit.DAYS.between(menstrualDate, today)
                val currentWeek = (daysSinceLastPeriod / 7).toInt() + 1

                // 임신 주차는 1~42주 범위로 제한
                val pregnancyWeek = when {
                    currentWeek < 1 -> 1
                    currentWeek > 42 -> 42
                    else -> currentWeek
                }

                Log.d(TAG, "Days since last period: $daysSinceLastPeriod")
                Log.d(TAG, "Calculated pregnancy week: $pregnancyWeek")

                return Pair(dueDate, pregnancyWeek)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing menstrual date: $menstrualDateString", e)
            }
        }

        // 생리일자가 없거나 파싱 실패 시 기본값
        Log.d(TAG, "Using default pregnancy info")
        return Pair(LocalDate.now().plusDays(280), 1)
    }
}