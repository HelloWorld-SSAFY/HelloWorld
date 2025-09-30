package com.ms.helloworld.dto.response

import com.google.gson.annotations.SerializedName

data class FileUploadResponse(
    @SerializedName("fileUrls") val fileUrls: List<String>
)