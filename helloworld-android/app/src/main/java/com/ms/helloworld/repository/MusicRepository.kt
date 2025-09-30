package com.ms.helloworld.repository

import com.ms.helloworld.dto.response.MusicDelivery
import com.ms.helloworld.network.api.WearApi
import javax.inject.Inject

class MusicRepository @Inject constructor(
    private val api: WearApi
) {
    suspend fun getMusicRecommendations(): Result<List<MusicDelivery>> {
        return try {
            val response = api.getMusicRecommendations(
                appToken = "e3d10cf9-bfad-43a7-9817-6b0b5dc2730c"
            )
            if (response.ok) {
                Result.success(response.deliveries)
            } else {
                Result.failure(Exception("API returned ok: false"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}