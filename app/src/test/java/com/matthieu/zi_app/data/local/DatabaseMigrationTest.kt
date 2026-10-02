package com.matthieu.zi_app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.data.local.entity.HskVocabEntity
import com.matthieu.zi_app.data.local.entity.ProgressRecordEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationTest {

    private lateinit var context: Context
    private val testDbName = "migration_test_db"
    private val testProgressDbName = "migration_test_progress_db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cleanupDatabases()
    }

    @After
    fun tearDown() {
        cleanupDatabases()
    }

    private fun cleanupDatabases() {
        context.deleteDatabase(testDbName)
        context.deleteDatabase(testProgressDbName)
    }

    @Test
    fun testMigration6To7PreservesUserDataAndRemovesObsoleteTables() {
        val dbFile = context.getDatabasePath(testDbName)
        dbFile.parentFile?.mkdirs()

        // 1. Create legacy v6 database
        val sqliteDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqliteDb.version = 6

        // Create core tables
        sqliteDb.execSQL("CREATE TABLE char_pinyin (char_pinyin_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, hanzi VARCHAR(2) NOT NULL, pinyin VARCHAR(10) NOT NULL, UNIQUE(hanzi, pinyin))")
        sqliteDb.execSQL("CREATE TABLE cword (cword_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, chinese VARCHAR(50) NOT NULL, pinyin VARCHAR(150) NOT NULL, length INTEGER DEFAULT 0, UNIQUE(chinese, pinyin))")
        sqliteDb.execSQL("CREATE TABLE cword_segment (cword_id INTEGER NOT NULL, char_pinyin_id INTEGER NOT NULL, pos INTEGER NOT NULL, UNIQUE(cword_id, pos))")
        sqliteDb.execSQL("CREATE TABLE eword (eng_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, cword_id INTEGER NOT NULL, translation VARCHAR(250) NOT NULL, UNIQUE(cword_id, translation))")
        sqliteDb.execSQL("CREATE TABLE leitner (hanzi VARCHAR(50) PRIMARY KEY NOT NULL, box INTEGER NOT NULL, exp_time INTEGER NOT NULL, num_tested INTEGER DEFAULT 0, num_wrong INTEGER DEFAULT 0, streak INTEGER DEFAULT 0, date_added INTEGER)")

        // Create legacy obsolete lesson tables
        sqliteDb.execSQL("CREATE TABLE lessons (lesson_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, list_order INTEGER DEFAULT 0, name VARCHAR(64) NOT NULL UNIQUE)")
        sqliteDb.execSQL("CREATE TABLE lesson_word (eng_id INTEGER NOT NULL, lesson_id INTEGER NOT NULL, UNIQUE(eng_id, lesson_id))")

        // Populate with user data
        sqliteDb.execSQL("INSERT INTO char_pinyin (char_pinyin_id, hanzi, pinyin) VALUES (1, '学', 'xue2'), (2, '习', 'xi2')")
        sqliteDb.execSQL("INSERT INTO cword (cword_id, chinese, pinyin, length) VALUES (1, '学习', 'xue2xi2', 2)")
        sqliteDb.execSQL("INSERT INTO cword_segment (cword_id, char_pinyin_id, pos) VALUES (1, 1, 0), (1, 2, 1)")
        sqliteDb.execSQL("INSERT INTO eword (eng_id, cword_id, translation) VALUES (1, 1, 'to study; to learn')")
        sqliteDb.execSQL("INSERT INTO leitner (hanzi, box, exp_time, num_tested, num_wrong, streak, date_added) VALUES ('学习', 3, 1728000000000, 5, 1, 2, 1700000000000)")
        sqliteDb.execSQL("INSERT INTO lessons (lesson_id, name) VALUES (1, 'Lesson 1')")
        sqliteDb.execSQL("INSERT INTO lesson_word (eng_id, lesson_id) VALUES (1, 1)")
        sqliteDb.close()

        // 2. Run MIGRATION_6_7
        val factory = FrameworkSQLiteOpenHelperFactory()
        val helper = factory.create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(testDbName)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(7) {
                    override fun onCreate(db: SupportSQLiteDatabase) {}
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        AndZiDatabase.MIGRATION_6_7.migrate(db)
                    }
                })
                .build()
        )
        val migratedDb = helper.writableDatabase

        // 3. Verify obsolete tables dropped
        val lessonTableCursor = migratedDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('lessons', 'lesson_word')")
        assertEquals("Expected lessons and lesson_word tables to be dropped", 0, lessonTableCursor.count)
        lessonTableCursor.close()

        // 4. Verify user study data intact
        val wordCursor = migratedDb.query("SELECT chinese, pinyin FROM cword WHERE cword_id = 1")
        assertTrue(wordCursor.moveToFirst())
        assertEquals("学习", wordCursor.getString(0))
        assertEquals("xue2xi2", wordCursor.getString(1))
        wordCursor.close()

        val leitnerCursor = migratedDb.query("SELECT box, exp_time, streak FROM leitner WHERE hanzi = '学习'")
        assertTrue(leitnerCursor.moveToFirst())
        assertEquals(3, leitnerCursor.getInt(0))
        assertEquals(1728000000000L, leitnerCursor.getLong(1))
        assertEquals(2, leitnerCursor.getInt(2))
        leitnerCursor.close()

        val transCursor = migratedDb.query("SELECT translation FROM eword WHERE cword_id = 1")
        assertTrue(transCursor.moveToFirst())
        assertEquals("to study; to learn", transCursor.getString(0))
        transCursor.close()

        migratedDb.close()
    }

    @Test
    fun testMigration7To8CreatesHskVocabTable() {
        val dbFile = context.getDatabasePath(testDbName)
        dbFile.parentFile?.mkdirs()

        // 1. Create v7 database
        val sqliteDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqliteDb.version = 7
        sqliteDb.execSQL("CREATE TABLE char_pinyin (char_pinyin_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, hanzi VARCHAR(2) NOT NULL, pinyin VARCHAR(10) NOT NULL, UNIQUE(hanzi, pinyin))")
        sqliteDb.execSQL("CREATE TABLE cword (cword_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, chinese VARCHAR(50) NOT NULL, pinyin VARCHAR(150) NOT NULL, length INTEGER DEFAULT 0, UNIQUE(chinese, pinyin))")
        sqliteDb.execSQL("CREATE TABLE leitner (hanzi VARCHAR(50) PRIMARY KEY NOT NULL, box INTEGER NOT NULL, exp_time INTEGER NOT NULL, num_tested INTEGER DEFAULT 0, num_wrong INTEGER DEFAULT 0, streak INTEGER DEFAULT 0, date_added INTEGER)")
        sqliteDb.execSQL("INSERT INTO leitner (hanzi, box, exp_time, num_tested, num_wrong, streak, date_added) VALUES ('你好', 2, 1710000000000, 3, 0, 3, 1700000000000)")
        sqliteDb.close()

        // 2. Run MIGRATION_7_8
        val factory = FrameworkSQLiteOpenHelperFactory()
        val helper = factory.create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(testDbName)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(8) {
                    override fun onCreate(db: SupportSQLiteDatabase) {}
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        AndZiDatabase.MIGRATION_7_8.migrate(db)
                    }
                })
                .build()
        )
        val migratedDb = helper.writableDatabase

        // 3. Verify hsk_vocab table was created
        val tableCursor = migratedDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name = 'hsk_vocab'")
        assertEquals("Expected hsk_vocab table to exist after migration", 1, tableCursor.count)
        tableCursor.close()

        // 4. Verify existing leitner data was preserved
        val leitnerCursor = migratedDb.query("SELECT box, streak FROM leitner WHERE hanzi = '你好'")
        assertTrue(leitnerCursor.moveToFirst())
        assertEquals(2, leitnerCursor.getInt(0))
        assertEquals(3, leitnerCursor.getInt(1))
        leitnerCursor.close()

        migratedDb.close()
    }

    @Test
    fun testFullDatabaseUpgradeFromV6ToV8WithRoom() = runBlocking {
        val dbFile = context.getDatabasePath(testDbName)
        dbFile.parentFile?.mkdirs()

        // 1. Create legacy v6 database with user data
        val sqliteDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqliteDb.version = 6
        sqliteDb.execSQL("CREATE TABLE char_pinyin (char_pinyin_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, hanzi TEXT NOT NULL, pinyin TEXT NOT NULL, UNIQUE(hanzi, pinyin))")
        sqliteDb.execSQL("CREATE INDEX char_pinyin_id ON char_pinyin(char_pinyin_id)")
        sqliteDb.execSQL("CREATE INDEX char_pinyin_hanzi ON char_pinyin(hanzi)")
        sqliteDb.execSQL("CREATE INDEX char_pinyin_pinyin ON char_pinyin(pinyin)")

        sqliteDb.execSQL("CREATE TABLE cword (cword_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, chinese TEXT NOT NULL, pinyin TEXT NOT NULL, length INTEGER DEFAULT 0, UNIQUE(chinese, pinyin))")
        sqliteDb.execSQL("CREATE INDEX cword_id ON cword(cword_id)")
        sqliteDb.execSQL("CREATE INDEX cword_chinese ON cword(chinese)")
        sqliteDb.execSQL("CREATE INDEX cword_pinyin ON cword(pinyin)")

        sqliteDb.execSQL("CREATE TABLE cword_segment (cword_id INTEGER NOT NULL, char_pinyin_id INTEGER NOT NULL, pos INTEGER NOT NULL, UNIQUE(cword_id, pos))")
        sqliteDb.execSQL("CREATE INDEX cword_segment_pos ON cword_segment(pos)")

        sqliteDb.execSQL("CREATE TABLE eword (eng_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, cword_id INTEGER NOT NULL, translation TEXT NOT NULL, UNIQUE(cword_id, translation))")

        sqliteDb.execSQL("CREATE TABLE leitner (hanzi TEXT PRIMARY KEY NOT NULL, box INTEGER NOT NULL, exp_time INTEGER NOT NULL, num_tested INTEGER DEFAULT 0, num_wrong INTEGER DEFAULT 0, streak INTEGER DEFAULT 0, date_added INTEGER NOT NULL)")

        sqliteDb.execSQL("CREATE TABLE lessons (lesson_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, list_order INTEGER DEFAULT 0, name TEXT NOT NULL UNIQUE)")
        sqliteDb.execSQL("CREATE TABLE lesson_word (eng_id INTEGER NOT NULL, lesson_id INTEGER NOT NULL, UNIQUE(eng_id, lesson_id))")

        // Populate with rich study data
        sqliteDb.execSQL("INSERT INTO char_pinyin (char_pinyin_id, hanzi, pinyin) VALUES (1, '朋', 'peng2'), (2, '友', 'you5')")
        sqliteDb.execSQL("INSERT INTO cword (cword_id, chinese, pinyin, length) VALUES (1, '朋友', 'peng2you5', 2)")
        sqliteDb.execSQL("INSERT INTO cword_segment (cword_id, char_pinyin_id, pos) VALUES (1, 1, 0), (1, 2, 1)")
        sqliteDb.execSQL("INSERT INTO eword (eng_id, cword_id, translation) VALUES (1, 1, 'friend')")
        sqliteDb.execSQL("INSERT INTO leitner (hanzi, box, exp_time, num_tested, num_wrong, streak, date_added) VALUES ('朋友', 4, 1750000000000, 8, 1, 4, 1680000000000)")
        sqliteDb.close()

        // 2. Open via Room at current version (8) with registered migrations
        val roomDb = Room.databaseBuilder(
            context,
            AndZiDatabase::class.java,
            testDbName
        )
            .addMigrations(*AndZiDatabase.ALL_MIGRATIONS)
            .build()

        // 3. Verify Room DAOs can query existing data flawlessly
        val leitnerRecord = roomDb.leitnerDao().getRecord("朋友")
        assertNotNull("Expected Leitner record for 朋友 to exist after migration", leitnerRecord)
        assertEquals(4, leitnerRecord!!.box)
        assertEquals(1750000000000L, leitnerRecord.expTime)
        assertEquals(8, leitnerRecord.numTested)
        assertEquals(1, leitnerRecord.numWrong)
        assertEquals(4, leitnerRecord.streak)
        assertEquals(1680000000000L, leitnerRecord.dateAdded)

        val wordsWithDetails = roomDb.vocabularyDao().getAllWordsWithDetails()
        assertEquals(1, wordsWithDetails.size)
        val word = wordsWithDetails.first()
        assertEquals("朋友", word.word.chinese)
        assertEquals("peng2you5", word.word.pinyin)
        assertEquals(1, word.translations.size)
        assertEquals("friend", word.translations.first().translation)
        assertEquals(2, word.characters.size)

        // 4. Verify new HskVocab functionality works on the upgraded database
        roomDb.vocabularyDao().insertHskVocab(
            listOf(HskVocabEntity(hanzi = "朋友", hskLevel = 1, hsk2Level = 1, freqRank = 150, isChar = 0))
        )
        val hskVocab = roomDb.vocabularyDao().getHskVocabForWord("朋友")
        assertNotNull(hskVocab)
        assertEquals(1, hskVocab!!.hskLevel)
        assertEquals(150, hskVocab.freqRank)

        roomDb.close()
    }

    @Test
    fun testProgressDatabaseMigration1To2() = runBlocking {
        val dbFile = context.getDatabasePath(testProgressDbName)
        dbFile.parentFile?.mkdirs()

        // 1. Create legacy v1 progress database (without zoom column)
        val sqliteDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqliteDb.version = 1
        sqliteDb.execSQL(
            """
            CREATE TABLE progress (
                date INTEGER PRIMARY KEY NOT NULL,
                total_box_1 INTEGER DEFAULT 0,
                total_box_2 INTEGER DEFAULT 0,
                total_box_3 INTEGER DEFAULT 0,
                total_box_4 INTEGER DEFAULT 0,
                total_box_5 INTEGER DEFAULT 0,
                expired_box_2 INTEGER DEFAULT 0,
                expired_box_3 INTEGER DEFAULT 0,
                expired_box_4 INTEGER DEFAULT 0,
                expired_box_5 INTEGER DEFAULT 0
            )
            """.trimIndent()
        )

        // Insert historic record
        val snapshotDate = 1700000000000L
        sqliteDb.execSQL(
            """
            INSERT INTO progress (date, total_box_1, total_box_2, total_box_3, total_box_4, total_box_5, expired_box_2, expired_box_3, expired_box_4, expired_box_5)
            VALUES ($snapshotDate, 10, 20, 30, 40, 50, 2, 3, 4, 5)
            """.trimIndent()
        )
        sqliteDb.close()

        // 2. Open via Room at version 2 with registered migrations
        val progressDb = Room.databaseBuilder(
            context,
            ProgressDatabase::class.java,
            testProgressDbName
        )
            .addMigrations(*ProgressDatabase.ALL_MIGRATIONS)
            .build()

        // 3. Verify existing data preserved and zoom column available
        val allProgress = progressDb.progressDao().getAllProgress()
        assertEquals(1, allProgress.size)
        val record = allProgress.first()
        assertEquals(snapshotDate, record.date)
        assertEquals(10, record.totalBox1)
        assertEquals(20, record.totalBox2)
        assertEquals(30, record.totalBox3)
        assertEquals(40, record.totalBox4)
        assertEquals(50, record.totalBox5)
        assertEquals(0, record.zoom) // default zoom

        // 4. Verify inserting new progress record with zoom works
        val newDate = 1710000000000L
        progressDb.progressDao().insertProgress(
            ProgressRecordEntity(
                date = newDate,
                totalBox1 = 5,
                totalBox2 = 15,
                totalBox3 = 25,
                totalBox4 = 35,
                totalBox5 = 45,
                expiredBox2 = 1,
                expiredBox3 = 2,
                expiredBox4 = 3,
                expiredBox5 = 4,
                zoom = 1
            )
        )

        val updatedProgress = progressDb.progressDao().getAllProgress()
        assertEquals(2, updatedProgress.size)
        val newRecord = updatedProgress.last()
        assertEquals(newDate, newRecord.date)
        assertEquals(1, newRecord.zoom)

        progressDb.close()
    }
}
