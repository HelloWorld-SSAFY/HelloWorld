package com.ms.helloworld.network.api

import com.ms.helloworld.dto.response.CaricatureResponse
import retrofit2.Response
import retrofit2.http.*

interface CaricatureApi {

    @GET("calendar/caricatures/from-photo/{diaryPhotoId}")
    suspend fun getCaricatureFromPhoto(
        @Path("diaryPhotoId") diaryPhotoId: Long
    ): Response<CaricatureResponse>

    @POST("calendar/caricatures/{diaryPhotoId}/caricature")
    suspend fun generateCaricature(
        @Path("diaryPhotoId") diaryPhotoId: Long
    ): Response<CaricatureResponse>
}