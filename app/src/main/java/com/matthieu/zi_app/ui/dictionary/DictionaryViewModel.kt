package com.matthieu.zi_app.ui.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import com.matthieu.zi_app.domain.srs.ReviewGrade
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DictionaryViewModel(
    private val dictionaryRepository: VocabularyRepository,
    private val localVocabularyRepository: VocabularyRepository,
    private val studyRepository: StudyRepository,
    private val debounceMillis: Long = 150L
) : ViewModel() {

    private val _uiState = MutableStateFlow(DictionaryUiState())
    val uiState: StateFlow<DictionaryUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        // Initial search for common characters
        onSearchQueryChanged("你")
    }

    fun onSearchQueryChanged(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        searchJob?.cancel()

        val trimmed = newQuery.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            if (debounceMillis > 0) {
                delay(debounceMillis)
            }
            _uiState.update { it.copy(isSearching = true) }
            try {
                val searchResults = dictionaryRepository.search(trimmed, limit = 60)
                _uiState.update {
                    it.copy(
                        results = searchResults,
                        isSearching = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        errorMessage = e.localizedMessage ?: "Search failed"
                    )
                }
            }
        }
    }

    fun selectWord(vocabulary: Vocabulary) {
        viewModelScope.launch {
            val hanzi = vocabulary.chinese
            val charCount = hanzi.codePointCount(0, hanzi.length)

            // Polyphonic readings if single character
            val polyReadings = if (charCount == 1) {
                dictionaryRepository.getVocabularyForChinese(hanzi)
            } else emptyList()

            // Containing compound words
            val containingCompounds = if (charCount == 1) {
                dictionaryRepository.getWordsContainingCharacter(hanzi).take(30)
            } else emptyList()

            // Constituent single characters if compound word
            val constituents = mutableListOf<String>()
            if (charCount > 1) {
                for (i in 0 until charCount) {
                    val codePoint = hanzi.codePointAt(i)
                    constituents.add(String(Character.toChars(codePoint)))
                }
            }

            // Check if word is already tracked in Leitner study
            val leitnerState = studyRepository.getLeitnerState(hanzi)

            _uiState.update {
                it.copy(
                    selectedWord = vocabulary,
                    selectedCharReadings = polyReadings,
                    containingCompounds = containingCompounds,
                    constituentCharacters = constituents,
                    isInStudyQueue = leitnerState != null,
                    currentBox = leitnerState?.box
                )
            }
        }
    }

    fun selectCharacterFromConstituent(char: String) {
        viewModelScope.launch {
            val vocList = dictionaryRepository.getVocabularyForChinese(char)
            val first = vocList.firstOrNull() ?: Vocabulary(chinese = char, pinyin = "", english = arrayListOf())
            selectWord(first)
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedWord = null,
                selectedCharReadings = emptyList(),
                containingCompounds = emptyList(),
                constituentCharacters = emptyList()
            )
        }
    }

    fun toggleAddToStudy(vocabulary: Vocabulary) {
        viewModelScope.launch {
            val hanzi = vocabulary.chinese
            val currentState = studyRepository.getLeitnerState(hanzi)

            if (currentState == null) {
                // Add to local vocabulary and initialize in Leitner study
                localVocabularyRepository.addVocabulary(vocabulary)
                studyRepository.recordReviewGrade(hanzi, ReviewGrade.AGAIN)
                val newState = studyRepository.getLeitnerState(hanzi)

                _uiState.update {
                    it.copy(
                        isInStudyQueue = true,
                        currentBox = newState?.box ?: 0
                    )
                }
            } else {
                // Restart / reset card
                studyRepository.restartStudy(hanzi)
                _uiState.update {
                    it.copy(
                        isInStudyQueue = true,
                        currentBox = 0
                    )
                }
            }
        }
    }

    companion object {
        fun provideFactory(
            dictionaryRepository: VocabularyRepository,
            localVocabularyRepository: VocabularyRepository,
            studyRepository: StudyRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DictionaryViewModel(
                    dictionaryRepository,
                    localVocabularyRepository,
                    studyRepository
                ) as T
            }
        }
    }
}
