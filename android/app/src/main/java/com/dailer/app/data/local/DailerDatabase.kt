package com.dailer.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dailer.app.data.local.dao.CallRecordDao
import com.dailer.app.data.local.dao.RelativeDao
import com.dailer.app.data.local.entity.CallRecordEntity
import com.dailer.app.data.local.entity.RelativeEntity

@Database(
    entities = [RelativeEntity::class, CallRecordEntity::class],
    version = 2,
    exportSchema = false
)
abstract class DailerDatabase : RoomDatabase() {

    abstract fun relativeDao(): RelativeDao
    abstract fun callRecordDao(): CallRecordDao

    companion object {
        @Volatile
        private var INSTANCE: DailerDatabase? = null

        fun getInstance(context: Context): DailerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DailerDatabase::class.java,
                    "dailer_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
