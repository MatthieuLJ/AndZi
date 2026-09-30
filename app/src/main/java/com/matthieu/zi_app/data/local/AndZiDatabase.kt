package com.matthieu.zi_app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.matthieu.zi_app.data.local.dao.LeitnerDao
import com.matthieu.zi_app.data.local.dao.LessonDao
import com.matthieu.zi_app.data.local.dao.VocabularyDao
import com.matthieu.zi_app.data.local.entity.CharPinyinEntity
import com.matthieu.zi_app.data.local.entity.ChineseWordEntity
import com.matthieu.zi_app.data.local.entity.EnglishTranslationEntity
import com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity
import com.matthieu.zi_app.data.local.entity.LessonEntity
import com.matthieu.zi_app.data.local.entity.LessonWordCrossRef
import com.matthieu.zi_app.data.local.entity.WordSegmentEntity

@Database(
    entities = [
        CharPinyinEntity::class,
        ChineseWordEntity::class,
        WordSegmentEntity::class,
        EnglishTranslationEntity::class,
        LessonEntity::class,
        LessonWordCrossRef::class,
        LeitnerRecordEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AndZiDatabase : RoomDatabase() {

    abstract fun vocabularyDao(): VocabularyDao
    abstract fun lessonDao(): LessonDao
    abstract fun leitnerDao(): LeitnerDao

    companion object {
        const val DATABASE_NAME = "HANZI_DB"
        const val CEDICT_DATABASE_NAME = "cedict_db"

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
                CEDICT_INSTANCE ?: buildDatabase(context, CEDICT_DATABASE_NAME).also { CEDICT_INSTANCE = it }
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
