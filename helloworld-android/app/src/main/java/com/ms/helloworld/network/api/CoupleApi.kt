package com.ms.helloworld.network.api

import com.ms.helloworld.dto.request.CoupleInviteRequest
import com.ms.helloworld.dto.response.CoupleInviteCodeResponse
import com.ms.helloworld.dto.response.MemberRegisterResponse
import retrofit2.http.*

interface CoupleApi {

    /**
     * 초대 코드 생성 (여성 전용)
     */
    @POST("user/api/couples/invite")
    suspend fun generateInviteCode(): CoupleInviteCodeResponse

    /**
     * 초대 코드로 커플 합류 (남성)
     */
    @POST("user/api/couples/join")
    suspend fun joinCouple(@Body request: CoupleInviteRequest): CoupleInviteCodeResponse

    /**
     * 커플 연결 해제
     */
    @DELETE("user/api/couples/divorce")
    suspend fun disconnectCouple(): MemberRegisterResponse


}