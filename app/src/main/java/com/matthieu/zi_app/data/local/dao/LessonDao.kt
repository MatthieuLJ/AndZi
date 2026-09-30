package com.matthieu.zi_app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.matthieu.zi_app.data.local.entity.LessonEntity
import com.matthieu.zi_app.data.local.entity.LessonWordCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonDao {

    @Query("SELECT * FROM lessons ORDER BY list_order ASC, name ASC")
    fun getAllLessonsFlow(): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons ORDER BY list_order ASC, name ASC")
    suspend fun getAllLessons(): List<LessonEntity>

    @Query("SELECT * FROM lessons WHERE lesson_id = :lessonId LIMIT 1")
    suspend fun getLessonById(lessonId: Long): LessonEntity?

    @Query("SELECT * FROM lessons WHERE name = :name LIMIT 1")
    suspend fun getLessonByName(name: String): LessonEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLesson(lesson: LessonEntity): Long

    @Update
    suspend fun updateLesson(lesson: LessonEntity)

    @Update
    suspend fun updateLessons(lessons: List<LessonEntity>)

    @Delete
    suspend fun deleteLesson(lesson: LessonEntity)

    @Query("DELETE FROM lessons WHERE lesson_id = :lessonId")
    suspend fun deleteLessonById(lessonId: Long)

    @Query("SELECT COUNT(*) FROM lessons")
    suspend fun getLessonCount(): Int

    // --- Lesson Word Associations ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun assignWordToLesson(crossRef: LessonWordCrossRef): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun assignWordsToLesson(crossRefs: List<LessonWordCrossRef>): List<Long>

    @Query("DELETE FROM lesson_word WHERE eng_id = :engId AND lesson_id = :lessonId")
    suspend fun removeWordFromLesson(engId: Long, lessonId: Long)

    @Query("DELETE FROM lesson_word WHERE eng_id = :engId")
    suspend fun removeWordFromAllLessons(engId: Long)

    @Query("SELECT lesson_id FROM lesson_word WHERE eng_id = :engId")
    suspend fun getLessonIdsForTranslation(engId: Long): List<Long>

    @Query("SELECT COUNT(DISTINCT e.cword_id) FROM lesson_word lw INNER JOIN eword e ON e.eng_id = lw.eng_id WHERE lw.lesson_id = :lessonId")
    suspend fun getWordCountForLesson(lessonId: Long): Int
}
