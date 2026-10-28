package com.matthieu.zi_app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.matthieu.zi_app.data.preferences.UserPreferences
import com.matthieu.zi_app.data.repository.ProgressRepository
import com.matthieu.zi_app.data.repository.StudyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val studyRepository: StudyRepository,
    private val progressRepository: ProgressRepository,
    private val userPreferences: UserPreferences? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val now = System.currentTimeMillis()
                val due = studyRepository.getNumExpired(now)
                val boxCounts = studyRepository.getBoxCounts()
                val boxMap = (0..9).associateWith { box ->
                    boxCounts.find { it.box == box }?.count ?: 0
                }
                val totalLearned = boxMap.values.sum()

                val streakDays = userPreferences?.let { prefs ->
                    val recorded = prefs.getStreakDays(now)
                    if (recorded == 0 && prefs.hasNoRecordedStudy() && totalLearned > 0) {
                        1
                    } else {
                        recorded
                    }
                } ?: if (totalLearned > 0) 1 else 0

                _uiState.update {
                    it.copy(
                        dueCount = due,
                        totalLearned = totalLearned,
                        boxDistribution = boxMap,
                        streakDays = streakDays,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Failed to load dashboard"
                    )
                }
            }
        }
    }

    companion object {
        fun provideFactory(
            studyRepository: StudyRepository,
            progressRepository: ProgressRepository,
            userPreferences: UserPreferences? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(studyRepository, progressRepository, userPreferences) as T
            }
        }
    }
}
