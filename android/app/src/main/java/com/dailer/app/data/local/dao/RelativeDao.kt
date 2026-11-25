package com.dailer.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dailer.app.data.local.entity.RelativeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RelativeDao {

    @Query("SELECT * FROM relatives ORDER BY name ASC")
    fun getAll(): Flow<List<RelativeEntity>>

    @Query("SELECT COUNT(*) FROM relatives")
    fun getEnrolledCount(): Flow<Int>

    @Query("SELECT * FROM relatives ORDER BY name ASC")
    suspend fun getAllRelativesSync(): List<RelativeEntity>

    @Query("SELECT * FROM relatives WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun findByPhoneNumber(phoneNumber: String): RelativeEntity?

    @Query("SELECT * FROM relatives WHERE enrollmentId = :enrollmentId LIMIT 1")
    suspend fun getByEnrollmentId(enrollmentId: String): RelativeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(relative: RelativeEntity): Long

    @Delete
    suspend fun delete(relative: RelativeEntity)
}
