package com.ms.helloworld.dto.response

import com.google.gson.annotations.SerializedName

data class DiaryListResponse(
    @SerializedName("content") val content: List<DiaryResponse>,
    @SerializedName("pageable") val pageable: PageableInfo?,
    @SerializedName("totalElements") val totalElements: Int,
    @SerializedName("totalPages") val totalPages: Int,
    @SerializedName("first") val first: Boolean,
    @SerializedName("last") val last: Boolean,
    @SerializedName("size") val size: Int,
    @SerializedName("number") val number: Int,
    @SerializedName("numberOfElements") val numberOfElements: Int,
    @SerializedName("empty") val empty: Boolean
)

data class PageableInfo(
    @SerializedName("sort") val sort: SortInfo?,
    @SerializedName("offset") val offset: Int,
    @SerializedName("pageSize") val pageSize: Int,
    @SerializedName("pageNumber") val pageNumber: Int,
    @SerializedName("paged") val paged: Boolean,
    @SerializedName("unpaged") val unpaged: Boolean
)

data class SortInfo(
    @SerializedName("empty") val empty: Boolean,
    @SerializedName("unsorted") val unsorted: Boolean,
    @SerializedName("sorted") val sorted: Boolean
)