package com.dailer.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class EnrollmentsResponse(
    @SerializedName("enrollments")
    val enrollments: List<RemoteEnrollment> = emptyList(),
    @SerializedName("count")
    val count: Int = 0
) {
    data class RemoteEnrollment(
        @SerializedName("enrollment_id")
        val enrollmentId: String,
        @SerializedName("name")
        val name: String,
        @SerializedName("phone_number")
        val phoneNumber: String,
        @SerializedName("relationship")
        val relationship: String = "Family"
    )
}
