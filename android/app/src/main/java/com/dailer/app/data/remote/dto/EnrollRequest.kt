package com.dailer.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Request payload for enrolling a relative's voiceprint.
 */
data class EnrollRequest(
    @SerializedName("name")
    val name: String,
    @SerializedName("phone_number")
    val phoneNumber: String,
    @SerializedName("audio_base64")
    val audioBase64: String,
    @SerializedName("relationship")
    val relationship: String
)
