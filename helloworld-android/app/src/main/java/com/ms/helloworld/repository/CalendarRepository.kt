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
            println("🌐 Repository - API 호출 직전:")
            println("   eventId: $eventId")
            println("   request: $request")
            println("   title: '${request.title}'")
            println("   memo: '${request.memo}'")
            println("   startAt: '${request.startAt}'")
            println("   endAt: '${request.endAt}'")
            println("   isRemind: ${request.isRemind}")
            println("   orderNo: ${request.orderNo}")

            val response = calendarApi.updateEvent(eventId, request)
            println("✅ Repository - API 응답 성공: $response")
            Result.success(response)
        } catch (e: Exception) {
            println("❌ Repository - API 호출 실패: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    suspend fun deleteEvent(eventId: Long): Result<Unit> {
        return try {
            println("🗑️ Repository - 삭제 API 호출: eventId=$eventId")
            val response = calendarApi.deleteEvent(eventId)
            if (response.isSuccessful) {
                println("✅ Repository - 삭제 API 응답 성공: ${response.code()}")
                Result.success(Unit)
            } else {
                println("❌ Repository - 삭제 API 응답 실패: ${response.code()} ${response.message()}")
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            println("❌ Repository - 삭제 API 호출 실패: ${e.message}")
            e.printStackTrace()
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
        from: String? = null,
        to: String? = null,
        page: Int? = null,
        size: Int? = null
    ): Result<CalendarEventsPageResponse> {
        return try {
            val response = calendarApi.getEvents(from, to, page, size)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}