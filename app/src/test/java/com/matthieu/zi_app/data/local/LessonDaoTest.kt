package com.matthieu.zi_app.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.data.local.dao.LessonDao
import com.matthieu.zi_app.data.local.dao.VocabularyDao
import com.matthieu.zi_app.data.local.entity.ChineseWordEntity
import com.matthieu.zi_app.data.local.entity.EnglishTranslationEntity
import com.matthieu.zi_app.data.local.entity.LessonEntity
import com.matthieu.zi_app.data.local.entity.LessonWordCrossRef
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LessonDaoTest {

    private lateinit var db: AndZiDatabase
    private lateinit var lessonDao: LessonDao
    private lateinit var vocDao: VocabularyDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AndZiDatabase.createInMemory(context)
        lessonDao = db.lessonDao()
        vocDao = db.vocabularyDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testLessonCrudAndReordering() = runBlocking {
        val id1 = lessonDao.insertLesson(LessonEntity(name = "Lesson 1", listOrder = 1))
        val id2 = lessonDao.insertLesson(LessonEntity(name = "Lesson 2", listOrder = 2))

        val all = lessonDao.getAllLessons()
        assertEquals(2, all.size)
        assertEquals("Lesson 1", all[0].name)
        assertEquals("Lesson 2", all[1].name)

        // Reorder
        lessonDao.updateLessons(
            listOf(
                LessonEntity(lessonId = id1, name = "Lesson 1", listOrder = 2),
                LessonEntity(lessonId = id2, name = "Lesson 2", listOrder = 1)
            )
        )

        val reordered = lessonDao.getAllLessons()
        assertEquals("Lesson 2", reordered[0].name)
        assertEquals("Lesson 1", reordered[1].name)

        // Delete
        lessonDao.deleteLessonById(id1)
        assertEquals(1, lessonDao.getLessonCount())
    }

    @Test
    fun testLessonWordAssignment() = runBlocking {
        val lessonId = lessonDao.insertLesson(LessonEntity(name = "HSK 1", listOrder = 1))
        val wordId = vocDao.insertWord(ChineseWordEntity(chinese = "吃", pinyin = "chi1", length = 1))
        val engId = vocDao.insertTranslation(EnglishTranslationEntity(cwordId = wordId, translation = "to eat"))

        lessonDao.assignWordToLesson(LessonWordCrossRef(engId = engId, lessonId = lessonId))

        val wordCount = lessonDao.getWordCountForLesson(lessonId)
        assertEquals(1, wordCount)

        val lessonIds = lessonDao.getLessonIdsForTranslation(engId)
        assertEquals(1, lessonIds.size)
        assertEquals(lessonId, lessonIds[0])

        // Remove
        lessonDao.removeWordFromLesson(engId, lessonId)
        assertEquals(0, lessonDao.getWordCountForLesson(lessonId))
    }
}
