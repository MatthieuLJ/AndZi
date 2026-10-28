package com.matthieu.zi_app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.matthieu.zi_app.data.local.dao.LeitnerDao
import com.matthieu.zi_app.data.local.dao.VocabularyDao
import com.matthieu.zi_app.data.local.entity.CharPinyinEntity
import com.matthieu.zi_app.data.local.entity.CharSimilarityEntity
import com.matthieu.zi_app.data.local.entity.ChineseWordEntity
import com.matthieu.zi_app.data.local.entity.EnglishTranslationEntity
import com.matthieu.zi_app.data.local.entity.HskVocabEntity
import com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity
import com.matthieu.zi_app.data.local.entity.WordSegmentEntity

import com.matthieu.zi_app.R
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CharPinyinEntity::class,
        ChineseWordEntity::class,
        WordSegmentEntity::class,
        EnglishTranslationEntity::class,
        LeitnerRecordEntity::class,
        HskVocabEntity::class,
        CharSimilarityEntity::class
    ],
    version = 9,
    exportSchema = true
)
abstract class AndZiDatabase : RoomDatabase() {

    abstract fun vocabularyDao(): VocabularyDao
    abstract fun leitnerDao(): LeitnerDao

    companion object {
        const val DATABASE_NAME = "HANZI_DB"
        const val CEDICT_DATABASE_NAME = "cedict_db"
        const val DATABASE_VERSION = 9
        const val ROOM_IDENTITY_HASH = "27a47e93ae1d280a71096c5fdf061e16"

        @Volatile
        private var INSTANCE: AndZiDatabase? = null

        @Volatile
        private var CEDICT_INSTANCE: AndZiDatabase? = null

        fun setTestInstance(instance: AndZiDatabase?) {
            INSTANCE = instance
        }

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

        @Synchronized
        fun ensureDictionaryExtracted(context: Context) {
            val dbFile = context.getDatabasePath(CEDICT_DATABASE_NAME)
            if (dbFile.exists() && dbFile.length() > 1_000_000L) {
                try {
                    val sdb = android.database.sqlite.SQLiteDatabase.openDatabase(
                        dbFile.path,
                        null,
                        android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
                    )
                    val currentVer = sdb.version
                    if (currentVer >= DATABASE_VERSION) {
                        sdb.execSQL("CREATE TABLE IF NOT EXISTS `leitner` (`hanzi` TEXT NOT NULL, `box` INTEGER NOT NULL, `exp_time` INTEGER NOT NULL, `num_tested` INTEGER NOT NULL DEFAULT 0, `num_wrong` INTEGER NOT NULL DEFAULT 0, `streak` INTEGER NOT NULL DEFAULT 0, `date_added` INTEGER NOT NULL, PRIMARY KEY(`hanzi`));")
                        sdb.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT);")
                        sdb.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '$ROOM_IDENTITY_HASH');")
                        sdb.close()
                        return
                    }
                    sdb.close()
                    // Outdated version on device, clean up and re-extract
                    context.deleteDatabase(CEDICT_DATABASE_NAME)
                } catch (e: Exception) {
                    e.printStackTrace()
                    context.deleteDatabase(CEDICT_DATABASE_NAME)
                }
            }

            // If an invalid / empty file exists, clean it up completely
            if (dbFile.exists()) {
                context.deleteDatabase(CEDICT_DATABASE_NAME)
            }

            val tempFile = File(dbFile.parentFile, "$CEDICT_DATABASE_NAME.${System.nanoTime()}.tmp")
            try {
                context.resources.openRawResource(R.raw.cedict_db).use { rawInput ->
                    ZipInputStream(BufferedInputStream(rawInput)).use { zis ->
                        val entry = zis.nextEntry
                        if (entry != null) {
                            tempFile.parentFile?.mkdirs()
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

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop legacy lesson tables
                db.execSQL("DROP TABLE IF EXISTS `lesson_word`")
                db.execSQL("DROP TABLE IF EXISTS `lessons`")

                // Ensure char_pinyin table and indexes
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `char_pinyin_new` (
                        `char_pinyin_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `hanzi` TEXT NOT NULL,
                        `pinyin` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                val charPinyinTableExists = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='char_pinyin'").use { it.count > 0 }
                if (charPinyinTableExists) {
                    db.execSQL("INSERT OR IGNORE INTO `char_pinyin_new` (`char_pinyin_id`, `hanzi`, `pinyin`) SELECT `char_pinyin_id`, `hanzi`, `pinyin` FROM `char_pinyin`")
                    db.execSQL("DROP TABLE `char_pinyin`")
                }
                db.execSQL("ALTER TABLE `char_pinyin_new` RENAME TO `char_pinyin`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `char_pinyin_id` ON `char_pinyin` (`char_pinyin_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `char_pinyin_hanzi` ON `char_pinyin` (`hanzi`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `char_pinyin_pinyin` ON `char_pinyin` (`pinyin`)")

                // Ensure cword table and indexes
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cword_new` (
                        `cword_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `chinese` TEXT NOT NULL,
                        `pinyin` TEXT NOT NULL,
                        `length` INTEGER DEFAULT 0
                    )
                    """.trimIndent()
                )
                val cwordTableExists = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='cword'").use { it.count > 0 }
                if (cwordTableExists) {
                    db.execSQL("INSERT OR IGNORE INTO `cword_new` (`cword_id`, `chinese`, `pinyin`, `length`) SELECT `cword_id`, `chinese`, `pinyin`, IFNULL(`length`, 0) FROM `cword`")
                    db.execSQL("DROP TABLE `cword`")
                }
                db.execSQL("ALTER TABLE `cword_new` RENAME TO `cword`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `cword_id` ON `cword` (`cword_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `cword_chinese` ON `cword` (`chinese`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `cword_pinyin` ON `cword` (`pinyin`)")

                // Recreate cword_segment to align composite primary keys and foreign keys with Room schema
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cword_segment_new` (
                        `cword_id` INTEGER NOT NULL,
                        `char_pinyin_id` INTEGER NOT NULL,
                        `pos` INTEGER NOT NULL,
                        PRIMARY KEY(`cword_id`, `pos`),
                        FOREIGN KEY(`cword_id`) REFERENCES `cword`(`cword_id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`char_pinyin_id`) REFERENCES `char_pinyin`(`char_pinyin_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                val segmentTableExists = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='cword_segment'").use { it.count > 0 }
                if (segmentTableExists) {
                    db.execSQL("INSERT OR IGNORE INTO `cword_segment_new` (`cword_id`, `char_pinyin_id`, `pos`) SELECT `cword_id`, `char_pinyin_id`, `pos` FROM `cword_segment`")
                    db.execSQL("DROP TABLE `cword_segment`")
                }
                db.execSQL("ALTER TABLE `cword_segment_new` RENAME TO `cword_segment`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `cword_segment_pos` ON `cword_segment` (`pos`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cword_segment_char_pinyin_id` ON `cword_segment` (`char_pinyin_id`)")

                // Recreate eword to align foreign keys with Room schema
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `eword_new` (
                        `eng_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `cword_id` INTEGER NOT NULL,
                        `translation` TEXT NOT NULL,
                        FOREIGN KEY(`cword_id`) REFERENCES `cword`(`cword_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                val ewordTableExists = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='eword'").use { it.count > 0 }
                if (ewordTableExists) {
                    db.execSQL("INSERT OR IGNORE INTO `eword_new` (`eng_id`, `cword_id`, `translation`) SELECT `eng_id`, `cword_id`, `translation` FROM `eword`")
                    db.execSQL("DROP TABLE `eword`")
                }
                db.execSQL("ALTER TABLE `eword_new` RENAME TO `eword`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_eword_cword_id` ON `eword` (`cword_id`)")

                // Recreate leitner table to ensure notNull constraints on all integer fields match Room schema
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `leitner_new` (
                        `hanzi` TEXT NOT NULL,
                        `box` INTEGER NOT NULL,
                        `exp_time` INTEGER NOT NULL,
                        `num_tested` INTEGER NOT NULL DEFAULT 0,
                        `num_wrong` INTEGER NOT NULL DEFAULT 0,
                        `streak` INTEGER NOT NULL DEFAULT 0,
                        `date_added` INTEGER NOT NULL,
                        PRIMARY KEY(`hanzi`)
                    )
                    """.trimIndent()
                )
                val leitnerTableExists = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='leitner'").use { it.count > 0 }
                if (leitnerTableExists) {
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO `leitner_new` (`hanzi`, `box`, `exp_time`, `num_tested`, `num_wrong`, `streak`, `date_added`)
                        SELECT `hanzi`, `box`, `exp_time`, IFNULL(`num_tested`, 0), IFNULL(`num_wrong`, 0), IFNULL(`streak`, 0), IFNULL(`date_added`, 0)
                        FROM `leitner`
                        """.trimIndent()
                    )
                    db.execSQL("DROP TABLE `leitner`")
                }
                db.execSQL("ALTER TABLE `leitner_new` RENAME TO `leitner`")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `hsk_vocab` (
                        `hanzi` TEXT NOT NULL,
                        `hsk_level` INTEGER NOT NULL,
                        `hsk2_level` INTEGER NOT NULL DEFAULT 0,
                        `freq_rank` INTEGER NOT NULL DEFAULT 999999,
                        `is_char` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`hanzi`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_hsk_level` ON `hsk_vocab` (`hsk_level`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_hsk_freq` ON `hsk_vocab` (`freq_rank`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `char_similarity` (
                        `hanzi` TEXT NOT NULL,
                        `similar_hanzi` TEXT NOT NULL,
                        `score` REAL NOT NULL,
                        `shared_component` TEXT,
                        PRIMARY KEY(`hanzi`, `similar_hanzi`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_char_similarity_hanzi_score` ON `char_similarity` (`hanzi`, `score`)")
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9
        )

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
                .addMigrations(*ALL_MIGRATIONS)
                .build()
        }
    }
}
