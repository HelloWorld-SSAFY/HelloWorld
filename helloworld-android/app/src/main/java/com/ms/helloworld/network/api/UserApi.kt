package com.ms.helloworld.network.api

import com.ms.helloworld.dto.request.MemberRegisterRequest
import com.ms.helloworld.dto.request.MemberUpdateRequest
import com.ms.helloworld.dto.request.CoupleUpdateRequest
import com.ms.helloworld.dto.request.CoupleCreateRequest
import com.ms.helloworld.dto.request.AvatarUrlRequest
import com.ms.helloworld.dto.response.MemberRegisterResponse
import com.ms.helloworld.dto.response.MemberProfileResponse
import com.ms.helloworld.dto.response.AvatarUrlResponse
import com.ms.helloworld.dto.response.CoupleProfile
import com.ms.helloworld.dto.response.CoupleDetailResponse
import retrofit2.http.*

interface UserApi {
    /**
     유저 등록
     **/
    @POST("user/api/users/register")
    @Headers(
        "Content-Type: application/json",
        "Accept: application/json"
    )
    suspend fun registerUser(
        @Body request: MemberRegisterRequest
    ): MemberRegisterResponse
    /**
    유저 등록
     **/
    @PUT("user/api/users/me")
    @Headers(
        "Content-Type: application/json",
        "Accept: application/json"
    )
    suspend fun updateProfile(
        @Body request: MemberUpdateRequest
    ): MemberRegisterResponse
    /**
    커플 생성 (여성만)
     **/
    @POST("user/api/couples")
    @Headers(
        "Content-Type: application/json",
        "Accept: application/json"
    )
    suspend fun createCouple(
        @Body request: CoupleCreateRequest
    ): MemberRegisterResponse
    /**
    커플 정보 업데이트
     **/
    @PUT("user/api/couples/me/couple")
    @Headers(
        "Content-Type: application/json",
        "Accept: application/json"
    )
    suspend fun updateCoupleInfo(
        @Body request: CoupleUpdateRequest
    ): MemberRegisterResponse
    /**
    유저 정보 조회
     **/
    @GET("user/api/users/me")
    @Headers(
        "Accept: application/json"
    )
    suspend fun getUserInfo(): MemberProfileResponse
    /**
    유저 등록
     **/
    @PUT("user/api/users/profile-image")
    @Headers(
        "Content-Type: application/json",
        "Accept: application/json"
    )
    suspend fun updateProfileImage(
        @Body request: AvatarUrlRequest
    ): AvatarUrlResponse

    /**
    커플 상세 정보 조회 (모든 정보 포함)
     **/
    @GET("user/api/couples/me/detail")
    @Headers(
        "Accept: application/json"
    )
    suspend fun getCoupleDetail(): retrofit2.Response<CoupleDetailResponse>
}