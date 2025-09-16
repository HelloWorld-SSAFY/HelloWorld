package com.ms.helloworld.network.api

import com.ms.helloworld.dto.request.CalendarCreateRequest
import com.ms.helloworld.dto.request.CalendarUpdateRequest
import com.ms.helloworld.dto.response.CalendarEventResponse
import com.ms.helloworld.dto.response.CalendarEventsPageResponse
import retrofit2.http.*

interface CalendarApi {

    @POST("calendar/calendar")
    suspend fun createEvent(
        @Query("coupleId") coupleId: Long,
        @Query("writerId") writerId: Long,
        @Body request: CalendarCreateRequest
    ): Map<String, String>

    @PUT("calendar/calendar/{eventId}")
    suspend fun updateEvent(
        @Path("eventId") eventId: Long,
        @Body request: CalendarUpdateRequest
    ): Map<String, Any>

    @DELETE("calendar/calendar/{eventId}")
    suspend fun deleteEvent(
        @Path("eventId") eventId: Long
    )

    @GET("calendar/calendar/{eventId}")
    suspend fun getEvent(
        @Path("eventId") eventId: Long
    ): CalendarEventResponse

    @GET("calendar/calendar/events")
    suspend fun getEvents(
        @Query("coupleId") coupleId: Long? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("page") page: Int? = null,
        @Query("size") size: Int? = null
    ): CalendarEventsPageResponse
}