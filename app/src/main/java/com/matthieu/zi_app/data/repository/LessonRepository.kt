package com.matthieu.zi_app.data.repository

import com.matthieu.zi_app.data.local.dao.LessonDao
import com.matthieu.zi_app.data.local.entity.LessonEntity
import com.matthieu.zi_app.data.local.entity.LessonWordCrossRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LessonRepository(
    private val lessonDao: LessonDao
) {

    fun getAllLessonsFlow(): Flow<List<LessonEntity>> {
        return lessonDao.getAllLessonsFlow()
    }

    suspend fun getAllLessons(): List<LessonEntity> = withContext(Dispatchers.IO) {
        lessonDao.getAllLessons()
    }

    suspend fun getLessonById(lessonId: Long): LessonEntity? = withContext(Dispatchers.IO) {
        lessonDao.getLessonById(lessonId)
    }

    suspend fun getLessonByName(name: String): LessonEntity? = withContext(Dispatchers.IO) {
        lessonDao.getLessonByName(name)
    }

    suspend fun createLesson(name: String, listOrder: Int = 0): Long = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return@withContext -1L
        val existing = lessonDao.getLessonByName(cleanName)
        if (existing != null) return@withContext existing.lessonId
        lessonDao.insertLesson(LessonEntity(name = cleanName, listOrder = listOrder))
    }

    suspend fun updateLesson(lesson: LessonEntity) = withContext(Dispatchers.IO) {
        lessonDao.updateLesson(lesson)
    }

    suspend fun reorderLessons(lessons: List<LessonEntity>) = withContext(Dispatchers.IO) {
        lessonDao.updateLessons(lessons)
    }

    suspend fun deleteLesson(lessonId: Long) = withContext(Dispatchers.IO) {
        lessonDao.deleteLessonById(lessonId)
    }

    suspend fun assignWordToLesson(engId: Long, lessonId: Long) = withContext(Dispatchers.IO) {
        lessonDao.assignWordToLesson(LessonWordCrossRef(engId = engId, lessonId = lessonId))
    }

    suspend fun removeWordFromLesson(engId: Long, lessonId: Long) = withContext(Dispatchers.IO) {
        lessonDao.removeWordFromLesson(engId, lessonId)
    }

    suspend fun getWordCountForLesson(lessonId: Long): Int = withContext(Dispatchers.IO) {
        lessonDao.getWordCountForLesson(lessonId)
    }
}
