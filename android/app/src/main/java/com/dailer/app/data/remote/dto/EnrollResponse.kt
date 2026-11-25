package com.dailer.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Response returned by the server after a voiceprint enrollment request.
 */
data class EnrollResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName(value = "enrollment_id", alternate = ["enrollmentId"])
    val enrollmentId: String,
    @SerializedName("message")
    val message: String
)
