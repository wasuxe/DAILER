package com.dailer.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an enrolled relative whose voiceprint is registered
 * on the verification backend for caller identity and anti-spoofing checks.
 */
@Entity(
    tableName = "relatives",
    indices = [
        Index(value = ["phoneNumber"]),
        Index(value = ["enrollmentId"])
    ]
)
data class RelativeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val phoneNumber: String,
    val enrollmentId: String,
    val enrolledAt: Long = System.currentTimeMillis(),
    val isVerified: Boolean = false,
    val relationship: String,
    val profileImageUri: String? = null
)
