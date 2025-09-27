package com.ms.helloworld.network.api

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
    ): Response<CaricatureGenerateResponse>
}

data class CaricatureResponse(
    val caricatureId: Long,
    val imageUrl: String,
    val createdAt: String
)

data class CaricatureGenerateResponse(
    val caricatureId: Long,
    val message: String
)