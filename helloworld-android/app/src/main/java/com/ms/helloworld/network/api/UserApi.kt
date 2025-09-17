package com.ms.helloworld.network.api

import com.ms.helloworld.dto.request.MemberRegisterRequest
import com.ms.helloworld.dto.request.MemberUpdateRequest
import com.ms.helloworld.dto.request.CoupleUpdateRequest
import com.ms.helloworld.dto.request.AvatarUrlRequest
import com.ms.helloworld.dto.response.MemberRegisterResponse
import com.ms.helloworld.dto.response.MemberProfileResponse
import com.ms.helloworld.dto.response.AvatarUrlResponse
import retrofit2.http.*

interface UserApi {

    @POST("user/api/users/register")
    suspend fun registerUser(
        @Body request: MemberRegisterRequest
    ): MemberRegisterResponse

    @PATCH("user/api/users/me")
    suspend fun updateProfile(
        @Body request: MemberUpdateRequest
    ): MemberRegisterResponse

    @PATCH("user/api/users/me/couple")
    suspend fun updateCoupleInfo(
        @Body request: CoupleUpdateRequest
    ): MemberRegisterResponse

    @GET("user/api/users/info")
    suspend fun getUserInfo(): MemberProfileResponse

    @PUT("user/api/users/profile-image")
    suspend fun updateProfileImage(
        @Body request: AvatarUrlRequest
    ): AvatarUrlResponse
}