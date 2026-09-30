package com.matthieu.zi_app.data

import android.content.Context
import com.matthieu.zi_app.data.local.AndZiDatabase
import com.matthieu.zi_app.data.local.ProgressDatabase
import com.matthieu.zi_app.data.repository.ProgressRepository
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository

interface AppContainer {
    val vocabularyRepository: VocabularyRepository
    val dictionaryRepository: VocabularyRepository
    val studyRepository: StudyRepository
    val progressRepository: ProgressRepository
    val userPreferences: com.matthieu.zi_app.data.preferences.UserPreferences
    val leitnerDao: com.matthieu.zi_app.data.local.dao.LeitnerDao
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    private val andZiDb by lazy { AndZiDatabase.getInstance(context) }
    private val cedictDb by lazy { AndZiDatabase.getDictionaryInstance(context) }
    private val progressDb by lazy { ProgressDatabase.getInstance(context) }

    override val leitnerDao: com.matthieu.zi_app.data.local.dao.LeitnerDao by lazy {
        andZiDb.leitnerDao()
    }

    override val userPreferences: com.matthieu.zi_app.data.preferences.UserPreferences by lazy {
        com.matthieu.zi_app.data.preferences.UserPreferences(context)
    }

    override val vocabularyRepository: VocabularyRepository by lazy {
        VocabularyRepository(andZiDb.vocabularyDao())
    }

    override val dictionaryRepository: VocabularyRepository by lazy {
        VocabularyRepository(cedictDb.vocabularyDao())
    }

    override val studyRepository: StudyRepository by lazy {
        StudyRepository(andZiDb.leitnerDao())
    }

    override val progressRepository: ProgressRepository by lazy {
        ProgressRepository(progressDb.progressDao(), andZiDb.leitnerDao())
    }
}
