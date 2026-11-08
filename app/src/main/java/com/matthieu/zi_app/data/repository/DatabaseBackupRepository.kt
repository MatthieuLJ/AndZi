package com.matthieu.zi_app.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.core.content.FileProvider
import com.matthieu.zi_app.data.local.AndZiDatabase
import com.matthieu.zi_app.data.local.ProgressDatabase
import com.matthieu.zi_app.data.local.entity.CharPinyinEntity
import com.matthieu.zi_app.data.local.entity.ChineseWordEntity
import com.matthieu.zi_app.data.local.entity.EnglishTranslationEntity
import com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity
import com.matthieu.zi_app.data.local.entity.ProgressRecordEntity
import com.matthieu.zi_app.data.local.entity.WordSegmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StudyBackupInspection(
    val wordCount: Int,
    val charCount: Int,
    val translationCount: Int,
    val scoreCount: Int,
    val conflictingScoreCount: Int,
    val tempFilePath: String
)

data class StudyMergeResult(
    val wordsAdded: Int,
    val translationsAdded: Int,
    val scoresImported: Int,
    val scoresUpdated: Int
)

class DatabaseBackupRepository(
    private val context: Context,
    private val andZiDb: AndZiDatabase,
    private val progressDb: ProgressDatabase
) {

    private val exportsDir: File
        get() = File(context.cacheDir, "exports").apply { mkdirs() }

    private val importsDir: File
        get() = File(context.cacheDir, "imports").apply { mkdirs() }

    /**
     * Flushes SQLite WAL and exports the active study database (Vocabulary + SRS).
     * If [includeScores] is false, SRS scores in the exported file are stripped.
     */
    suspend fun exportStudyDatabase(outputStream: OutputStream, includeScores: Boolean) = withContext(Dispatchers.IO) {
        checkpointDatabase(andZiDb)

        val dbFile = getDatabaseFile(andZiDb, AndZiDatabase.DATABASE_NAME)
        val tempExportFile = File(exportsDir, "temp_export_${System.currentTimeMillis()}.azdb")
        try {
            copyFile(dbFile, tempExportFile)

            if (!includeScores) {
                // Strip Leitner scores in the exported copy if leitner table exists
                val sdb = SQLiteDatabase.openDatabase(
                    tempExportFile.path,
                    null,
                    SQLiteDatabase.OPEN_READWRITE
                )
                try {
                    val hasLeitner = sdb.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name='leitner'",
                        null
                    ).use { it.count > 0 }
                    if (hasLeitner) {
                        sdb.execSQL("DELETE FROM leitner;")
                        sdb.execSQL("VACUUM;")
                    }
                } finally {
                    sdb.close()
                }
            }

            FileInputStream(tempExportFile).use { input ->
                input.copyTo(outputStream)
            }
            outputStream.flush()
        } finally {
            if (tempExportFile.exists()) {
                tempExportFile.delete()
            }
        }
    }

    /**
     * Creates a temporary exported study database file in cache for sharing via Android Sharesheet.
     */
    suspend fun createStudyDatabaseShareFile(includeScores: Boolean): File = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val fileName = "AndZiDB_${dateFormat.format(Date())}.azdb"
        val shareFile = File(exportsDir, fileName)

        FileOutputStream(shareFile).use { output ->
            exportStudyDatabase(output, includeScores)
        }
        shareFile
    }

    /**
     * Generates a content URI for sharing a file via FileProvider.
     */
    fun getShareUri(file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    /**
     * Flushes SQLite WAL and exports the active historical progress analytics database.
     */
    suspend fun exportProgressDatabase(outputStream: OutputStream) = withContext(Dispatchers.IO) {
        checkpointDatabase(progressDb)

        val dbFile = getDatabaseFile(progressDb, ProgressDatabase.DATABASE_NAME)
        val tempExportFile = File(exportsDir, "temp_progress_export_${System.currentTimeMillis()}.azdb")
        try {
            copyFile(dbFile, tempExportFile)
            FileInputStream(tempExportFile).use { input ->
                input.copyTo(outputStream)
            }
            outputStream.flush()
        } finally {
            if (tempExportFile.exists()) {
                tempExportFile.delete()
            }
        }
    }

    /**
     * Creates a temporary exported progress database file in cache for sharing via Android Sharesheet.
     */
    suspend fun createProgressDatabaseShareFile(): File = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val fileName = "AndZiProgress_${dateFormat.format(Date())}.azdb"
        val shareFile = File(exportsDir, fileName)

        FileOutputStream(shareFile).use { output ->
            exportProgressDatabase(output)
        }
        shareFile
    }

    /**
     * Inspects an incoming study database from an InputStream and prepares a [StudyBackupInspection].
     * Detects word counts, character counts, translations, SRS scores, and conflicting memory scores.
     */
    suspend fun inspectIncomingStudyDatabase(inputStream: InputStream): StudyBackupInspection = withContext(Dispatchers.IO) {
        val tempImportFile = File(importsDir, "import_inspect_${System.currentTimeMillis()}.azdb")
        FileOutputStream(tempImportFile).use { output ->
            inputStream.copyTo(output)
        }

        val sdb = try {
            SQLiteDatabase.openDatabase(tempImportFile.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            tempImportFile.delete()
            throw IllegalArgumentException("The selected file is not a valid SQLite database.", e)
        }

        try {
            // Verify table cword exists
            val hasCword = sdb.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='cword'",
                null
            ).use { it.count > 0 }

            if (!hasCword) {
                throw IllegalArgumentException("Invalid AndZi backup: Missing vocabulary data (cword table).")
            }

            var wordCount = 0
            sdb.rawQuery("SELECT COUNT(*) FROM cword", null).use {
                if (it.moveToFirst()) wordCount = it.getInt(0)
            }

            var charCount = 0
            val hasCharPinyin = sdb.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='char_pinyin'",
                null
            ).use { it.count > 0 }
            if (hasCharPinyin) {
                sdb.rawQuery("SELECT COUNT(*) FROM char_pinyin", null).use {
                    if (it.moveToFirst()) charCount = it.getInt(0)
                }
            }

            var translationCount = 0
            val hasEword = sdb.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='eword'",
                null
            ).use { it.count > 0 }
            if (hasEword) {
                sdb.rawQuery("SELECT COUNT(*) FROM eword", null).use {
                    if (it.moveToFirst()) translationCount = it.getInt(0)
                }
            }

            var scoreCount = 0
            var conflictingScoreCount = 0
            val hasLeitner = sdb.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='leitner'",
                null
            ).use { it.count > 0 }

            if (hasLeitner) {
                val currentRecords = andZiDb.leitnerDao().getAllRecords().associateBy { it.hanzi }

                sdb.rawQuery("SELECT hanzi, box, exp_time, num_tested FROM leitner", null).use { cursor ->
                    val hanziCol = cursor.getColumnIndex("hanzi")
                    val boxCol = cursor.getColumnIndex("box")
                    val expCol = cursor.getColumnIndex("exp_time")
                    val testedCol = cursor.getColumnIndex("num_tested")

                    while (cursor.moveToNext()) {
                        scoreCount++
                        val hanzi = if (hanziCol >= 0) cursor.getString(hanziCol) else null
                        val box = if (boxCol >= 0) cursor.getInt(boxCol) else 0
                        val exp = if (expCol >= 0) cursor.getLong(expCol) else 0L
                        val tested = if (testedCol >= 0) cursor.getInt(testedCol) else 0

                        if (hanzi != null && currentRecords.containsKey(hanzi)) {
                            val current = currentRecords[hanzi]!!
                            if (current.box != box || current.expTime != exp || current.numTested != tested) {
                                conflictingScoreCount++
                            }
                        }
                    }
                }
            }

            StudyBackupInspection(
                wordCount = wordCount,
                charCount = charCount,
                translationCount = translationCount,
                scoreCount = scoreCount,
                conflictingScoreCount = conflictingScoreCount,
                tempFilePath = tempImportFile.absolutePath
            )
        } catch (e: Exception) {
            sdb.close()
            tempImportFile.delete()
            throw e
        } finally {
            sdb.close()
        }
    }

    /**
     * Safely merges vocabulary and Leitner scores from an inspected database file into active database.
     * [overwriteScoresOnConflict]: If true, conflicting scores are overwritten with incoming values.
     *                              If false, existing scores for existing cards are preserved intact.
     */
    suspend fun mergeStudyDatabase(
        inspection: StudyBackupInspection,
        overwriteScoresOnConflict: Boolean
    ): StudyMergeResult = withContext(Dispatchers.IO) {
        val tempFile = File(inspection.tempFilePath)
        if (!tempFile.exists()) {
            throw IllegalStateException("Backup inspection file no longer exists.")
        }

        val sdb = SQLiteDatabase.openDatabase(tempFile.path, null, SQLiteDatabase.OPEN_READONLY)
        var wordsAdded = 0
        var translationsAdded = 0
        var scoresImported = 0
        var scoresUpdated = 0

        val vocabularyDao = andZiDb.vocabularyDao()
        val leitnerDao = andZiDb.leitnerDao()

        try {
            // 1. Merge char_pinyin
            val oldToNewCharPinyinMap = mutableMapOf<Long, Long>()
            val hasCharPinyin = sdb.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='char_pinyin'",
                null
            ).use { it.count > 0 }

            if (hasCharPinyin) {
                sdb.rawQuery("SELECT char_pinyin_id, hanzi, pinyin FROM char_pinyin", null).use { cursor ->
                    val idCol = cursor.getColumnIndex("char_pinyin_id")
                    val hanziCol = cursor.getColumnIndex("hanzi")
                    val pinyinCol = cursor.getColumnIndex("pinyin")

                    while (cursor.moveToNext()) {
                        val oldId = if (idCol >= 0) cursor.getLong(idCol) else -1L
                        val hanzi = if (hanziCol >= 0) cursor.getString(hanziCol) else ""
                        val pinyin = if (pinyinCol >= 0) cursor.getString(pinyinCol) else ""

                        if (hanzi.isNotEmpty() && pinyin.isNotEmpty()) {
                            val existing = vocabularyDao.getCharPinyin(hanzi, pinyin)
                            val newId = if (existing != null) {
                                existing.charPinyinId
                            } else {
                                vocabularyDao.insertCharPinyin(CharPinyinEntity(hanzi = hanzi, pinyin = pinyin))
                            }
                            if (oldId != -1L) {
                                oldToNewCharPinyinMap[oldId] = newId
                            }
                        }
                    }
                }
            }

            // 2. Read segments from incoming DB for quick lookup
            val incomingSegments = mutableMapOf<Long, MutableList<Pair<Int, Long>>>()
            val hasSegments = sdb.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='cword_segment'",
                null
            ).use { it.count > 0 }

            if (hasSegments) {
                sdb.rawQuery("SELECT cword_id, pos, char_pinyin_id FROM cword_segment ORDER BY pos ASC", null).use { cursor ->
                    val wordIdCol = cursor.getColumnIndex("cword_id")
                    val posCol = cursor.getColumnIndex("pos")
                    val cpIdCol = cursor.getColumnIndex("char_pinyin_id")

                    while (cursor.moveToNext()) {
                        val wId = cursor.getLong(wordIdCol)
                        val pos = cursor.getInt(posCol)
                        val cpId = cursor.getLong(cpIdCol)
                        incomingSegments.getOrPut(wId) { mutableListOf() }.add(Pair(pos, cpId))
                    }
                }
            }

            // 3. Read translations from incoming DB for quick lookup
            val incomingTranslations = mutableMapOf<Long, MutableList<String>>()
            val hasEword = sdb.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='eword'",
                null
            ).use { it.count > 0 }

            if (hasEword) {
                sdb.rawQuery("SELECT cword_id, translation FROM eword", null).use { cursor ->
                    val wordIdCol = cursor.getColumnIndex("cword_id")
                    val transCol = cursor.getColumnIndex("translation")

                    while (cursor.moveToNext()) {
                        val wId = cursor.getLong(wordIdCol)
                        val trans = cursor.getString(transCol).trim()
                        if (trans.isNotEmpty()) {
                            incomingTranslations.getOrPut(wId) { mutableListOf() }.add(trans)
                        }
                    }
                }
            }

            // 4. Merge cword and translations
            sdb.rawQuery("SELECT cword_id, chinese, pinyin, length FROM cword", null).use { cursor ->
                val idCol = cursor.getColumnIndex("cword_id")
                val chCol = cursor.getColumnIndex("chinese")
                val piCol = cursor.getColumnIndex("pinyin")
                val lenCol = cursor.getColumnIndex("length")

                while (cursor.moveToNext()) {
                    val oldWordId = cursor.getLong(idCol)
                    val chinese = cursor.getString(chCol).trim()
                    val pinyin = cursor.getString(piCol).trim()
                    val length = if (lenCol >= 0 && !cursor.isNull(lenCol)) {
                        cursor.getInt(lenCol)
                    } else {
                        chinese.codePointCount(0, chinese.length)
                    }

                    if (chinese.isEmpty() || pinyin.isEmpty()) continue

                    val existingWord = vocabularyDao.getWord(chinese, pinyin)
                    val targetWordId: Long

                    if (existingWord == null) {
                        targetWordId = vocabularyDao.insertWord(
                            ChineseWordEntity(chinese = chinese, pinyin = pinyin, length = length)
                        )
                        wordsAdded++

                        // Insert segments if mapped
                        val segList = incomingSegments[oldWordId]
                        if (!segList.isNullOrEmpty()) {
                            val newSegments = segList.mapNotNull { (pos, oldCpId) ->
                                val newCpId = oldToNewCharPinyinMap[oldCpId]
                                if (newCpId != null) {
                                    WordSegmentEntity(cwordId = targetWordId, charPinyinId = newCpId, pos = pos)
                                } else null
                            }
                            if (newSegments.isNotEmpty()) {
                                vocabularyDao.insertSegments(newSegments)
                            }
                        }
                    } else {
                        targetWordId = existingWord.cwordId
                    }

                    // Merge translations
                    val wordTranslations = incomingTranslations[oldWordId] ?: emptyList()
                    if (wordTranslations.isNotEmpty()) {
                        val existingTranslations = vocabularyDao.getTranslationsForWord(targetWordId)
                            .map { it.translation }
                            .toSet()

                        for (trans in wordTranslations) {
                            if (!existingTranslations.contains(trans)) {
                                vocabularyDao.insertTranslation(
                                    EnglishTranslationEntity(cwordId = targetWordId, translation = trans)
                                )
                                translationsAdded++
                            }
                        }
                    }
                }
            }

            // 5. Merge Leitner scores
            val hasLeitner = sdb.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='leitner'",
                null
            ).use { it.count > 0 }

            if (hasLeitner) {
                val currentRecords = leitnerDao.getAllRecords().associateBy { it.hanzi }

                sdb.rawQuery(
                    "SELECT * FROM leitner",
                    null
                ).use { cursor ->
                    val hanziCol = cursor.getColumnIndex("hanzi")
                    val boxCol = cursor.getColumnIndex("box")
                    val expCol = cursor.getColumnIndex("exp_time")
                    val testedCol = cursor.getColumnIndex("num_tested")
                    val wrongCol = cursor.getColumnIndex("num_wrong")
                    val streakCol = cursor.getColumnIndex("streak")
                    val addedCol = cursor.getColumnIndex("date_added")
                    val customCol = cursor.getColumnIndex("custom_data")

                    val recordsToInsert = mutableListOf<LeitnerRecordEntity>()

                    while (cursor.moveToNext()) {
                        val hanzi = if (hanziCol >= 0) cursor.getString(hanziCol) else continue
                        val box = if (boxCol >= 0) cursor.getInt(boxCol) else 0
                        val expTime = if (expCol >= 0) cursor.getLong(expCol) else 0L
                        val tested = if (testedCol >= 0) cursor.getInt(testedCol) else 0
                        val wrong = if (wrongCol >= 0) cursor.getInt(wrongCol) else 0
                        val streak = if (streakCol >= 0) cursor.getInt(streakCol) else 0
                        val dateAdded = if (addedCol >= 0) cursor.getLong(addedCol) else System.currentTimeMillis()
                        val customData = if (customCol >= 0 && !cursor.isNull(customCol)) cursor.getString(customCol) else "{}"

                        val record = LeitnerRecordEntity(
                            hanzi = hanzi,
                            box = box,
                            expTime = expTime,
                            numTested = tested,
                            numWrong = wrong,
                            streak = streak,
                            dateAdded = dateAdded,
                            customData = customData
                        )

                        val existing = currentRecords[hanzi]
                        if (existing == null) {
                            recordsToInsert.add(record)
                            scoresImported++
                        } else {
                            if (overwriteScoresOnConflict) {
                                recordsToInsert.add(record)
                                scoresUpdated++
                            }
                        }
                    }

                    if (recordsToInsert.isNotEmpty()) {
                        leitnerDao.insertOrUpdateRecords(recordsToInsert)
                    }
                }
            }

            StudyMergeResult(
                wordsAdded = wordsAdded,
                translationsAdded = translationsAdded,
                scoresImported = scoresImported,
                scoresUpdated = scoresUpdated
            )
        } finally {
            sdb.close()
            cleanupTempFile(inspection.tempFilePath)
        }
    }

    /**
     * Imports and merges historical progress analytics snapshots from an input stream into the progress database.
     */
    suspend fun importProgressDatabase(inputStream: InputStream): Int = withContext(Dispatchers.IO) {
        val tempImportFile = File(importsDir, "import_progress_${System.currentTimeMillis()}.azdb")
        FileOutputStream(tempImportFile).use { output ->
            inputStream.copyTo(output)
        }

        val sdb = try {
            SQLiteDatabase.openDatabase(tempImportFile.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            tempImportFile.delete()
            throw IllegalArgumentException("The selected file is not a valid SQLite database.", e)
        }

        try {
            val hasProgress = sdb.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='progress'",
                null
            ).use { it.count > 0 }

            if (!hasProgress) {
                throw IllegalArgumentException("Invalid progress backup: Missing progress table.")
            }

            val records = mutableListOf<ProgressRecordEntity>()
            sdb.rawQuery("SELECT * FROM progress", null).use { cursor ->
                val dateCol = cursor.getColumnIndex("date")
                val b1Col = cursor.getColumnIndex("total_box_1")
                val b2Col = cursor.getColumnIndex("total_box_2")
                val b3Col = cursor.getColumnIndex("total_box_3")
                val b4Col = cursor.getColumnIndex("total_box_4")
                val b5Col = cursor.getColumnIndex("total_box_5")
                val b6Col = cursor.getColumnIndex("total_box_6")
                val b7Col = cursor.getColumnIndex("total_box_7")
                val b8Col = cursor.getColumnIndex("total_box_8")
                val b9Col = cursor.getColumnIndex("total_box_9")
                val b10Col = cursor.getColumnIndex("total_box_10")
                val exp2Col = cursor.getColumnIndex("expired_box_2")
                val exp3Col = cursor.getColumnIndex("expired_box_3")
                val exp4Col = cursor.getColumnIndex("expired_box_4")
                val exp5Col = cursor.getColumnIndex("expired_box_5")
                val exp6Col = cursor.getColumnIndex("expired_box_6")
                val exp7Col = cursor.getColumnIndex("expired_box_7")
                val exp8Col = cursor.getColumnIndex("expired_box_8")
                val exp9Col = cursor.getColumnIndex("expired_box_9")
                val exp10Col = cursor.getColumnIndex("expired_box_10")
                val zoomCol = cursor.getColumnIndex("zoom")

                while (cursor.moveToNext()) {
                    val date = cursor.getLong(dateCol)
                    val b1 = if (b1Col >= 0) cursor.getInt(b1Col) else 0
                    val b2 = if (b2Col >= 0) cursor.getInt(b2Col) else 0
                    val b3 = if (b3Col >= 0) cursor.getInt(b3Col) else 0
                    val b4 = if (b4Col >= 0) cursor.getInt(b4Col) else 0
                    val b5 = if (b5Col >= 0) cursor.getInt(b5Col) else 0
                    val b6 = if (b6Col >= 0) cursor.getInt(b6Col) else 0
                    val b7 = if (b7Col >= 0) cursor.getInt(b7Col) else 0
                    val b8 = if (b8Col >= 0) cursor.getInt(b8Col) else 0
                    val b9 = if (b9Col >= 0) cursor.getInt(b9Col) else 0
                    val b10 = if (b10Col >= 0) cursor.getInt(b10Col) else 0
                    val exp2 = if (exp2Col >= 0) cursor.getInt(exp2Col) else 0
                    val exp3 = if (exp3Col >= 0) cursor.getInt(exp3Col) else 0
                    val exp4 = if (exp4Col >= 0) cursor.getInt(exp4Col) else 0
                    val exp5 = if (exp5Col >= 0) cursor.getInt(exp5Col) else 0
                    val exp6 = if (exp6Col >= 0) cursor.getInt(exp6Col) else 0
                    val exp7 = if (exp7Col >= 0) cursor.getInt(exp7Col) else 0
                    val exp8 = if (exp8Col >= 0) cursor.getInt(exp8Col) else 0
                    val exp9 = if (exp9Col >= 0) cursor.getInt(exp9Col) else 0
                    val exp10 = if (exp10Col >= 0) cursor.getInt(exp10Col) else 0
                    val zoom = if (zoomCol >= 0) cursor.getInt(zoomCol) else 0

                    records.add(
                        ProgressRecordEntity(
                            date = date,
                            totalBox1 = b1,
                            totalBox2 = b2,
                            totalBox3 = b3,
                            totalBox4 = b4,
                            totalBox5 = b5,
                            totalBox6 = b6,
                            totalBox7 = b7,
                            totalBox8 = b8,
                            totalBox9 = b9,
                            totalBox10 = b10,
                            expiredBox2 = exp2,
                            expiredBox3 = exp3,
                            expiredBox4 = exp4,
                            expiredBox5 = exp5,
                            expiredBox6 = exp6,
                            expiredBox7 = exp7,
                            expiredBox8 = exp8,
                            expiredBox9 = exp9,
                            expiredBox10 = exp10,
                            zoom = zoom
                        )
                    )
                }
            }

            if (records.isNotEmpty()) {
                progressDb.progressDao().insertProgressList(records)
            }
            records.size
        } finally {
            sdb.close()
            tempImportFile.delete()
        }
    }

    fun cleanupTempFile(path: String) {
        val file = File(path)
        if (file.exists()) {
            file.delete()
        }
    }

    private fun getDatabaseFile(db: androidx.room.RoomDatabase, defaultName: String): File {
        val path = db.openHelper.writableDatabase.path
        if (path != null) {
            val file = File(path)
            if (file.exists()) return file
        }
        val dbFile = context.getDatabasePath(defaultName)
        if (dbFile.exists()) return dbFile
        throw IllegalStateException("Database file not found for $defaultName.")
    }

    private fun checkpointDatabase(db: androidx.room.RoomDatabase) {
        try {
            val sdb = db.openHelper.writableDatabase
            sdb.query("PRAGMA wal_checkpoint(TRUNCATE);").use { cursor ->
                cursor.moveToFirst()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun copyFile(src: File, dst: File) {
        FileInputStream(src).use { input ->
            FileOutputStream(dst).use { output ->
                input.copyTo(output)
                output.flush()
            }
        }
    }
}
