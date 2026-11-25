package com.dailer.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Request payload for verifying an incoming/active call's audio sample.
 */
data class VerifyRequest(
    @SerializedName("audio_base64")
    val audioBase64: String,
    @SerializedName("phone_number")
    val phoneNumber: String,
    @SerializedName("claimed_relative_id")
    val claimedRelativeId: String? = null
)
