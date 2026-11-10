package com.matthieu.zi_app.ui.badges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.matthieu.zi_app.data.preferences.UserPreferences
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import com.matthieu.zi_app.domain.badges.HskBadgeTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BadgesViewModel(
    private val userPreferences: UserPreferences,
    private val studyRepository: StudyRepository? = null,
    private val dictionaryRepository: VocabularyRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(BadgesUiState(isLoading = true))
    val uiState: StateFlow<BadgesUiState> = _uiState.asStateFlow()

    init {
        loadBadges()
    }

    fun loadBadges() {
        viewModelScope.launch {
            try {
                val hskTracker = if (studyRepository != null && dictionaryRepository != null) {
                    HskBadgeTracker(studyRepository, dictionaryRepository, userPreferences)
                } else null

                hskTracker?.checkAndUnlockHskBadges()
                val hskProgress = hskTracker?.getHskProgress()

                val summary = userPreferences.getBadgesSummary(hskProgress = hskProgress)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        summary = summary,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Failed to load badges"
                    )
                }
            }
        }
    }

    companion object {
        fun provideFactory(
            userPreferences: UserPreferences,
            studyRepository: StudyRepository? = null,
            dictionaryRepository: VocabularyRepository? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BadgesViewModel(userPreferences, studyRepository, dictionaryRepository) as T
            }
        }
    }
}
