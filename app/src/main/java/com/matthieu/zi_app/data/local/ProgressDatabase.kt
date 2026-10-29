package com.matthieu.zi_app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.matthieu.zi_app.data.local.dao.ProgressDao
import com.matthieu.zi_app.data.local.entity.ProgressRecordEntity

@Database(
    entities = [ProgressRecordEntity::class],
    version = 3,
    exportSchema = true
)
abstract class ProgressDatabase : RoomDatabase() {

    abstract fun progressDao(): ProgressDao

    companion object {
        const val DATABASE_NAME = "progress_db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cursor = db.query("PRAGMA table_info(progress)")
                var hasZoom = false
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex != -1 && cursor.getString(nameIndex) == "zoom") {
                        hasZoom = true
                        break
                    }
                }
                cursor.close()
                if (!hasZoom) {
                    db.execSQL("ALTER TABLE `progress` ADD COLUMN `zoom` INTEGER DEFAULT 0")
                }
                db.execSQL("CREATE INDEX IF NOT EXISTS `progress_date` ON `progress` (`date`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cursor = db.query("PRAGMA table_info(progress)")
                val existingColumns = mutableSetOf<String>()
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex != -1) {
                        existingColumns.add(cursor.getString(nameIndex))
                    }
                }
                cursor.close()

                for (i in 6..10) {
                    val totalCol = "total_box_$i"
                    if (!existingColumns.contains(totalCol)) {
                        db.execSQL("ALTER TABLE `progress` ADD COLUMN `$totalCol` INTEGER DEFAULT 0")
                    }
                    val expCol = "expired_box_$i"
                    if (!existingColumns.contains(expCol)) {
                        db.execSQL("ALTER TABLE `progress` ADD COLUMN `$expCol` INTEGER DEFAULT 0")
                    }
                }
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3
        )

        @Volatile
        private var INSTANCE: ProgressDatabase? = null

        fun setTestInstance(instance: ProgressDatabase?) {
            INSTANCE = instance
        }

        fun getInstance(context: Context): ProgressDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ProgressDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(*ALL_MIGRATIONS)
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
