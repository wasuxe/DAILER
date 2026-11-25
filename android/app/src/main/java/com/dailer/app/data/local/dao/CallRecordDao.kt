package com.dailer.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dailer.app.data.local.entity.CallRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for managing [CallRecordEntity] call verification records.
 */
@Dao
interface CallRecordDao {

    @Query("SELECT * FROM call_records ORDER BY timestamp DESC")
    fun getAll(): Flow<List<CallRecordEntity>>

    @Query("SELECT * FROM call_records ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<CallRecordEntity>

    @Query("SELECT * FROM call_records WHERE phoneNumber = :phoneNumber ORDER BY timestamp DESC")
    suspend fun getByPhoneNumber(phoneNumber: String): List<CallRecordEntity>

    @Query("SELECT * FROM call_records WHERE isUploaded = 0 ORDER BY timestamp ASC")
    suspend fun getPending(): List<CallRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: CallRecordEntity): Long

    @Update
    suspend fun update(record: CallRecordEntity)

    @Query("UPDATE call_records SET isUploaded = :isUploaded WHERE id = :id")
    suspend fun markUploaded(id: Long, isUploaded: Boolean = true)

    @Query("DELETE FROM call_records WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int
}
