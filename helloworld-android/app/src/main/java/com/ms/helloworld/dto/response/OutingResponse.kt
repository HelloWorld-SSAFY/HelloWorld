package com.ms.helloworld.dto.response

data class OutingResponse(
    val ok: Boolean,
    val category: String,
    val session_id: String,
    val has_delivery: Boolean,
    val count: Int,
    val deliveries: List<OutingDelivery>
)

data class OutingDelivery(
    val delivery_id: String,
    val place_id: Int,
    val title: String?,  // null 가능성 추가
    val lat: Double,
    val lng: Double,
    val address: String?,  // null 가능성 있음
    val place_category: String,
    val weather_gate: String?,  // null 가능성 있음
    val reason: String,
    val rank: Int,
    val created_at: String,
    val meta: Map<String, Any> = emptyMap()  // 기본값 설정
)
