package com.matthieu.zi_app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.matthieu.zi_app.data.local.dao.ProgressDao
import com.matthieu.zi_app.data.local.entity.ProgressRecordEntity

@Database(
    entities = [ProgressRecordEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ProgressDatabase : RoomDatabase() {

    abstract fun progressDao(): ProgressDao

    companion object {
        const val DATABASE_NAME = "progress_db"

        @Volatile
        private var INSTANCE: ProgressDatabase? = null

        fun getInstance(context: Context): ProgressDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ProgressDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }

        fun createInMemory(context: Context): ProgressDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                ProgressDatabase::class.java
            ).allowMainThreadQueries().build()
        }
    }
}
