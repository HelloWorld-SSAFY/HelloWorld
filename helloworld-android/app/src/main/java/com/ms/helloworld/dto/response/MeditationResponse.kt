package com.ms.helloworld.dto.response

data class MeditationResponse(
    val ok: Boolean,
    val category: String,
    val session_id: String,
    val has_delivery: Boolean,
    val count: Int,
    val deliveries: List<MeditationDelivery>
)

data class MeditationDelivery(
    val delivery_id: String,
    val content_id: Int,
    val title: String,
    val provider: String,
    val url: String,
    val thumbnail: String,
    val duration_sec: Int?,
    val rank: Int,
    val score: Double,
    val created_at: String,
    val reason: String,
    val meta: Map<String, Any>
)