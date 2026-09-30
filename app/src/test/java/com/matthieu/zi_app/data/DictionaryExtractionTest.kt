package com.matthieu.zi_app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.data.local.AndZiDatabase
import com.matthieu.zi_app.data.repository.VocabularyRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DictionaryExtractionTest {

    @Test
    fun testDictionaryExtractionAndSearchForNi() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()

            // Get dictionary database instance (extracts from R.raw.cedict_db)
            val dictDb = AndZiDatabase.getDictionaryInstance(context)
            val dictRepo = VocabularyRepository(dictDb.vocabularyDao())

            // Search for common character "你"
            val searchResults = dictRepo.search("你", limit = 10)
            assertNotNull(searchResults)
            assertFalse("Expected dictionary search results for '你' to not be empty", searchResults.isEmpty())

            val niWord = searchResults.find { it.chinese == "你" }
            assertNotNull("Expected '你' to be found in CC-CEDICT dictionary", niWord)
            assertTrue(niWord!!.pinyin.contains("ni3", ignoreCase = true))

            dictDb.close()
        }
    }

    @Test
    fun testDatabaseUpgradeFromLegacyVersion6() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test_upgrade_legacy_db"
            context.deleteDatabase(dbName)

            val dbFile = context.getDatabasePath(dbName)
            dbFile.parentFile?.mkdirs()

            // Create an SQLite database pretending to be from an older app build (version 6 with old hash)
            val sdb = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            sdb.version = 6
            sdb.execSQL("CREATE TABLE IF NOT EXISTS `leitner` (`hanzi` TEXT NOT NULL, `box` INTEGER NOT NULL, `exp_time` INTEGER NOT NULL, `num_tested` INTEGER NOT NULL DEFAULT 0, `num_wrong` INTEGER NOT NULL DEFAULT 0, `streak` INTEGER NOT NULL DEFAULT 0, `date_added` INTEGER NOT NULL, PRIMARY KEY(`hanzi`));")
            sdb.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT);")
            sdb.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, 'aba1735d8769cb27cae008884489bff2');")
            sdb.close()

            // Now open with Room (which is at DATABASE_VERSION = 7)
            val db = androidx.room.Room.databaseBuilder(
                context,
                AndZiDatabase::class.java,
                dbName
            ).fallbackToDestructiveMigration().build()

            val leitnerState = db.leitnerDao().getRecord("你")
            // Leitner state should be null since it's empty, and no IllegalStateException should be thrown
            org.junit.Assert.assertNull(leitnerState)
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun testWordSelectionWithLeitnerState() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()

            // Get both user DB (HANZI_DB) and dictionary DB (cedict_db)
            val userDb = AndZiDatabase.getInstance(context)
            val dictDb = AndZiDatabase.getDictionaryInstance(context)

            val dictRepo = VocabularyRepository(dictDb.vocabularyDao())
            val searchResults = dictRepo.search("你", limit = 5)
            assertFalse(searchResults.isEmpty())

            val niWord = searchResults.first { it.chinese == "你" }
            assertNotNull(niWord)

            // Query leitner state from user DB (this reproduces the exact call made when tapping a word)
            val leitnerState = userDb.leitnerDao().getRecord(niWord.chinese)
            // Should succeed without exception
            userDb.close()
            dictDb.close()
        }
    }
}
