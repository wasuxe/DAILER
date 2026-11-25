package com.dailer.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_records")
data class CallRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val callId: String,
    val phoneNumber: String,
    val relativeId: String? = null,
    val verdict: String,
    val confidence: Float = 0f,
    val syntheticScore: Float = 0f,
    val speakerScore: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val audioPath: String? = null,
    val callDirection: String = "INCOMING",
    val durationMs: Long = 0L,
    val isUploaded: Boolean = false,
    val reason: String? = null
) {
    companion object {
        const val STATUS_VERIFIED = "VERIFIED"
        const val STATUS_WARNING = "WARNING"
        const val STATUS_UNKNOWN = "UNKNOWN"
    }
}
