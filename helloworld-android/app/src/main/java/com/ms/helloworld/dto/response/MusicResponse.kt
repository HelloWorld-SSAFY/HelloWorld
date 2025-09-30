package com.ms.helloworld.dto.response

data class MusicResponse(
    val ok: Boolean,
    val category: String,
    val session_id: String,
    val has_delivery: Boolean,
    val count: Int,
    val deliveries: List<MusicDelivery>
)

data class MusicDelivery(
    val delivery_id: String,
    val content_id: Int,
    val title: String,
    val provider: String,
    val url: String,
    val thumbnail: String,
    val duration_sec: Int?,  // null 가능성 있음
    val rank: Int,
    val score: Double,
    val created_at: String,
    val reason: String,
    val meta: Map<String, Any> // JSON 오브젝트 → 키-값 맵핑
)
