package com.ms.helloworld.repository

import com.ms.helloworld.dto.response.OutingDelivery
import com.ms.helloworld.network.api.WearApi
import javax.inject.Inject

class OutingRepository @Inject constructor(
    private val api: WearApi
) {
    suspend fun getOutingRecommendations(): Result<List<OutingDelivery>> {
        return try {
            val response = api.getOutingRecommendations(
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