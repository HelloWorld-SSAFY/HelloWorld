package com.ms.helloworld.repository

import android.util.Log
import com.ms.helloworld.dto.request.MemberRegisterRequest
import com.ms.helloworld.dto.request.MemberUpdateRequest
import com.ms.helloworld.dto.request.CoupleUpdateRequest
import com.ms.helloworld.dto.request.AvatarUrlRequest
import com.ms.helloworld.dto.response.MemberProfileResponse
import com.ms.helloworld.dto.response.MemberRegisterResponse
import com.ms.helloworld.dto.response.AvatarUrlResponse
import com.ms.helloworld.dto.response.MomProfile
import com.ms.helloworld.network.api.UserApi
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MomProfileRepository @Inject constructor(
    private val userApi: UserApi
) {

    companion object {
        private const val TAG = "MomProfileRepository"
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
                Log.d(TAG, "Couple id: ${couple.id}")
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
            userApi.registerUser(request)
        } catch (e: Exception) {
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

    suspend fun updateCoupleInfo(request: CoupleUpdateRequest): MemberRegisterResponse? {
        return try {
            userApi.updateCoupleInfo(request)
        } catch (e: Exception) {
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

        return if (couple?.pregnancyWeek != null && couple.dueDate != null) {
            Log.d(TAG, "Using complete couple data")
            Log.d(TAG, "Couple userAId: ${couple.userAId}")
            Log.d(TAG, "Couple userBId: ${couple.userBId}")

            // member 정보를 사용하여 닉네임 결정 (현재 로그인한 사용자)
            val nickname = member?.nickname ?: "엄마"
            Log.d(TAG, "Using member nickname: $nickname")

            val momProfile = MomProfile(
                nickname = nickname,
                pregnancyWeek = couple.pregnancyWeek,
                dueDate = LocalDate.parse(couple.dueDate, DateTimeFormatter.ISO_LOCAL_DATE)
            )
            Log.d(TAG, "Created MomProfile with couple data: $momProfile")
            momProfile
        } else if (member != null) {
            // couple 정보가 없거나 불완전하면 member 정보로 계산
            Log.d(TAG, "Couple data incomplete, calculating from member data")
            Log.d(TAG, "Member nickname: ${member.nickname}")
            Log.d(TAG, "Member gender: ${member.gender}")
            Log.d(TAG, "Member menstrualDate: ${member.menstrualDate}")

            val nickname = member.nickname ?: "엄마"

            // 생리일자 기준으로 예정일과 임신 주차 계산
            val (calculatedDueDate, calculatedWeek) = calculatePregnancyInfo(member.menstrualDate)

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