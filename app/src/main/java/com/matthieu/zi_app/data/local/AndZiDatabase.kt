package com.matthieu.zi_app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.matthieu.zi_app.data.local.dao.LeitnerDao
import com.matthieu.zi_app.data.local.dao.VocabularyDao
import com.matthieu.zi_app.data.local.entity.CharPinyinEntity
import com.matthieu.zi_app.data.local.entity.ChineseWordEntity
import com.matthieu.zi_app.data.local.entity.EnglishTranslationEntity
import com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity
import com.matthieu.zi_app.data.local.entity.WordSegmentEntity

import com.matthieu.zi_app.R
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

@Database(
    entities = [
        CharPinyinEntity::class,
        ChineseWordEntity::class,
        WordSegmentEntity::class,
        EnglishTranslationEntity::class,
        LeitnerRecordEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AndZiDatabase : RoomDatabase() {

    abstract fun vocabularyDao(): VocabularyDao
    abstract fun leitnerDao(): LeitnerDao

    companion object {
        const val DATABASE_NAME = "HANZI_DB"
        const val CEDICT_DATABASE_NAME = "cedict_db"
        const val DATABASE_VERSION = 7
        const val ROOM_IDENTITY_HASH = "5f32e97941947e98cdc84cc4c3d894ec"

        @Volatile
        private var INSTANCE: AndZiDatabase? = null

        @Volatile
        private var CEDICT_INSTANCE: AndZiDatabase? = null

        fun getInstance(context: Context): AndZiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context, DATABASE_NAME).also { INSTANCE = it }
            }
        }

        fun getDictionaryInstance(context: Context): AndZiDatabase {
            return CEDICT_INSTANCE ?: synchronized(this) {
                CEDICT_INSTANCE ?: run {
                    ensureDictionaryExtracted(context.applicationContext)
                    buildDatabase(context, CEDICT_DATABASE_NAME).also { CEDICT_INSTANCE = it }
                }
            }
        }

        fun ensureDictionaryExtracted(context: Context) {
            val dbFile = context.getDatabasePath(CEDICT_DATABASE_NAME)
            if (dbFile.exists() && dbFile.length() > 1_000_000L) {
                try {
                    if (dbFile.exists()) {
                        val sdb = android.database.sqlite.SQLiteDatabase.openDatabase(dbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)
                        sdb.version = DATABASE_VERSION
                        sdb.execSQL("CREATE TABLE IF NOT EXISTS `leitner` (`hanzi` TEXT NOT NULL, `box` INTEGER NOT NULL, `exp_time` INTEGER NOT NULL, `num_tested` INTEGER NOT NULL DEFAULT 0, `num_wrong` INTEGER NOT NULL DEFAULT 0, `streak` INTEGER NOT NULL DEFAULT 0, `date_added` INTEGER NOT NULL, PRIMARY KEY(`hanzi`));")
                        sdb.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT);")
                        sdb.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '$ROOM_IDENTITY_HASH');")
                        sdb.close()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return
            }

            // If an invalid / empty file exists, clean it up completely
            if (dbFile.exists()) {
                context.deleteDatabase(CEDICT_DATABASE_NAME)
            }

            dbFile.parentFile?.mkdirs()
            val tempFile = File(dbFile.parentFile, "$CEDICT_DATABASE_NAME.tmp")
            try {
                context.resources.openRawResource(R.raw.cedict_db).use { rawInput ->
                    ZipInputStream(BufferedInputStream(rawInput)).use { zis ->
                        val entry = zis.nextEntry
                        if (entry != null) {
                            FileOutputStream(tempFile).use { fos ->
                                val buffer = ByteArray(8192)
                                var len: Int
                                while (zis.read(buffer).also { len = it } != -1) {
                                    fos.write(buffer, 0, len)
                                }
                                fos.flush()
                            }
                            zis.closeEntry()
                        }
                    }
                }
                if (tempFile.exists() && tempFile.length() > 1_000_000L) {
                    if (dbFile.exists()) {
                        context.deleteDatabase(CEDICT_DATABASE_NAME)
                    }
                    if (!tempFile.renameTo(dbFile)) {
                        tempFile.inputStream().use { input ->
                            dbFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        tempFile.delete()
                    }
                    if (dbFile.exists()) {
                        try {
                            val sdb = android.database.sqlite.SQLiteDatabase.openDatabase(dbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)
                            sdb.version = DATABASE_VERSION
                            sdb.execSQL("CREATE TABLE IF NOT EXISTS `leitner` (`hanzi` TEXT NOT NULL, `box` INTEGER NOT NULL, `exp_time` INTEGER NOT NULL, `num_tested` INTEGER NOT NULL DEFAULT 0, `num_wrong` INTEGER NOT NULL DEFAULT 0, `streak` INTEGER NOT NULL DEFAULT 0, `date_added` INTEGER NOT NULL, PRIMARY KEY(`hanzi`));")
                            sdb.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT);")
                            sdb.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '$ROOM_IDENTITY_HASH');")
                            sdb.close()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }

        fun createInMemory(context: Context): AndZiDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AndZiDatabase::class.java
            ).allowMainThreadQueries().build()
        }

        private fun buildDatabase(context: Context, dbName: String): AndZiDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AndZiDatabase::class.java,
                dbName
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
