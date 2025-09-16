package com.ms.helloworld.repository

import com.ms.helloworld.dto.request.CalendarCreateRequest
import com.ms.helloworld.dto.request.CalendarUpdateRequest
import com.ms.helloworld.dto.response.CalendarEventResponse
import com.ms.helloworld.dto.response.CalendarEventsPageResponse
import com.ms.helloworld.network.api.CalendarApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarRepository @Inject constructor(
    private val calendarApi: CalendarApi
) {
    
    suspend fun createEvent(
        coupleId: Long,
        writerId: Long,
        request: CalendarCreateRequest
    ): Result<Map<String, String>> {
        return try {
            val response = calendarApi.createEvent(coupleId, writerId, request)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateEvent(
        eventId: Long,
        request: CalendarUpdateRequest
    ): Result<Map<String, Any>> {
        return try {
            val response = calendarApi.updateEvent(eventId, request)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun deleteEvent(eventId: Long): Result<Unit> {
        return try {
            calendarApi.deleteEvent(eventId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getEvent(eventId: Long): Result<CalendarEventResponse> {
        return try {
            val response = calendarApi.getEvent(eventId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getEvents(
        coupleId: Long? = null,
        from: String? = null,
        to: String? = null,
        page: Int? = null,
        size: Int? = null
    ): Result<CalendarEventsPageResponse> {
        return try {
            val response = calendarApi.getEvents(coupleId, from, to, page, size)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}