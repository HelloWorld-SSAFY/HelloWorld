package com.ms.helloworld.repository

import android.util.Log
import com.ms.helloworld.dto.response.OutingDelivery
import com.ms.helloworld.network.api.WearApi
import javax.inject.Inject

private const val TAG = "싸피_OutingRepository"
class OutingRepository @Inject constructor(
    private val api: WearApi
) {
    suspend fun getOutingRecommendations(): Result<List<OutingDelivery>> {
        return try {
            val response = api.getOutingRecommendations(
                appToken = "e3d10cf9-bfad-43a7-9817-6b0b5dc2730c"
            )
            response.deliveries.forEachIndexed { index, delivery ->
                Log.d(TAG, "외출 추천 [$index]:")
                Log.d(TAG, "  - category: ${response.category}")
                Log.d(TAG, "  - delivery_id: ${delivery.delivery_id}")
                Log.d(TAG, "  - place_id: ${delivery.place_id}")
                Log.d(TAG, "  - title: ${delivery.title}")
                Log.d(TAG, "  - lat: ${delivery.lat}")
                Log.d(TAG, "  - lng: ${delivery.lng}")
                Log.d(TAG, "  - address: ${delivery.address}")
                Log.d(TAG, "  - place_category: ${delivery.place_category}")
                Log.d(TAG, "  - weather_gate: ${delivery.weather_gate}")
                Log.d(TAG, "  - reason: ${delivery.reason}")
                Log.d(TAG, "  - rank: ${delivery.rank}")
                Log.d(TAG, "  - created_at: ${delivery.created_at}")
                Log.d(TAG, "  - meta: ${delivery.meta}")
            }
            
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