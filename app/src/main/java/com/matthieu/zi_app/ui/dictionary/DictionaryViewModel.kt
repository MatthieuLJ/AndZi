package com.matthieu.zi_app.ui.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import com.matthieu.zi_app.domain.recommendation.WordRecommendationEngine
import com.matthieu.zi_app.domain.srs.ReviewGrade
import kotlinx.coroutines.CancellationException
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
    private val wordRecommendationEngine: WordRecommendationEngine = WordRecommendationEngine(dictionaryRepository),
    private val debounceMillis: Long = 150L
) : ViewModel() {

    private val _uiState = MutableStateFlow(DictionaryUiState())
    val uiState: StateFlow<DictionaryUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var suggestionJob: Job? = null
    private val recentSuggestions = ArrayDeque<String>()

    init {
        loadInitialSuggestion()
        loadHskStats()
    }

    fun loadHskStats() {
        viewModelScope.launch {
            try {
                val totalCounts = dictionaryRepository.getHskLevelCounts()
                val studyRecords = studyRepository.getAllRecords()
                val studiedHanzi = studyRecords.map { it.hanzi }.distinct()
                val hskEntities = dictionaryRepository.getHskVocabForWords(studiedHanzi)

                val studiedPerLevel = mutableMapOf<Int, MutableSet<String>>()
                for (entity in hskEntities) {
                    if (entity.hskLevel in 1..7) {
                        studiedPerLevel.getOrPut(entity.hskLevel) { mutableSetOf() }.add(entity.hanzi)
                    }
                }

                val stats = (1..7).associateWith { lvl ->
                    val total = totalCounts[lvl] ?: 0
                    val studied = studiedPerLevel[lvl]?.size ?: 0
                    com.matthieu.zi_app.ui.hsk.HskLevelProgress(level = lvl, totalWords = total, studiedWords = studied)
                }

                _uiState.update { it.copy(hskLevelStats = stats) }
            } catch (e: Exception) {
                // Ignore stats load failure
            }
        }
    }

    fun loadInitialSuggestion() {
        suggestionJob?.cancel()
        suggestionJob = viewModelScope.launch {
            try {
                val knownChars = localVocabularyRepository.getAllDistinctHanzi().toSet()
                val deckRecords = studyRepository.getAllRecords().map { it.hanzi }.toSet()
                val allUserVoc = localVocabularyRepository.getAllVocabulary().map { it.chinese }.toSet()
                val deckWords = deckRecords + allUserVoc
                val excluded = (recentSuggestions + listOfNotNull(_uiState.value.suggestedWord)).toSet()

                val recommendation = wordRecommendationEngine.recommendNextWord(
                    knownCharacters = knownChars,
                    deckWords = deckWords,
                    excludedWords = excluded
                )
                recentSuggestions.addLast(recommendation.word)
                while (recentSuggestions.size > 25) {
                    recentSuggestions.removeFirst()
                }

                _uiState.update {
                    it.copy(
                        query = recommendation.word,
                        suggestedWord = recommendation.word,
                        suggestionReason = recommendation.reason.formatDisplay(),
                        isAutoSuggested = true
                    )
                }
                executeSearch(recommendation.word, skipDebounce = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Fallback to "你" if recommendation fails
                onSearchQueryChanged("你")
            }
        }
    }

    fun refreshSuggestion() {
        suggestionJob?.cancel()
        suggestionJob = viewModelScope.launch {
            try {
                val knownChars = localVocabularyRepository.getAllDistinctHanzi().toSet()
                val deckRecords = studyRepository.getAllRecords().map { it.hanzi }.toSet()
                val allUserVoc = localVocabularyRepository.getAllVocabulary().map { it.chinese }.toSet()
                val deckWords = deckRecords + allUserVoc
                val excluded = (recentSuggestions + listOfNotNull(_uiState.value.suggestedWord)).toSet()

                val recommendation = wordRecommendationEngine.recommendNextWord(
                    knownCharacters = knownChars,
                    deckWords = deckWords,
                    excludedWords = excluded
                )
                recentSuggestions.addLast(recommendation.word)
                while (recentSuggestions.size > 25) {
                    recentSuggestions.removeFirst()
                }

                _uiState.update {
                    it.copy(
                        query = recommendation.word,
                        suggestedWord = recommendation.word,
                        suggestionReason = recommendation.reason.formatDisplay(),
                        isAutoSuggested = true
                    )
                }
                executeSearch(recommendation.word, skipDebounce = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep current state if refresh fails
            }
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        suggestionJob?.cancel()
        val isSuggested = newQuery == _uiState.value.suggestedWord && _uiState.value.suggestedWord != null
        _uiState.update {
            it.copy(
                query = newQuery,
                isAutoSuggested = isSuggested,
                suggestionReason = if (isSuggested) it.suggestionReason else null
            )
        }
        executeSearch(newQuery, skipDebounce = false)
    }

    private fun executeSearch(searchQuery: String, skipDebounce: Boolean) {
        searchJob?.cancel()

        val trimmed = searchQuery.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            if (!skipDebounce && debounceMillis > 0) {
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

            // Check existing local translations
            val localVocabs = localVocabularyRepository.getVocabularyForChinese(hanzi)
            val localMatching = localVocabs.find { it.pinyin == vocabulary.pinyin } ?: localVocabs.firstOrNull()

            val isStudying = localMatching != null && localMatching.english.isNotEmpty()
            val selectedTranslations = if (isStudying) {
                localMatching!!.english.toSet()
            } else {
                emptySet()
            }

            val mergedEnglish = ArrayList((vocabulary.english + (localMatching?.english ?: emptyList())).distinct())
            val displayWord = Vocabulary(
                chinese = vocabulary.chinese,
                pinyin = vocabulary.pinyin.ifEmpty { localMatching?.pinyin ?: "" },
                english = mergedEnglish
            )

            _uiState.update {
                it.copy(
                    selectedWord = displayWord,
                    selectedCharReadings = polyReadings,
                    containingCompounds = containingCompounds,
                    constituentCharacters = constituents,
                    isInStudyQueue = isStudying,
                    currentBox = if (isStudying) (leitnerState?.box ?: 0) else null,
                    selectedTranslations = selectedTranslations
                )
            }
        }
    }

    fun selectCharacterFromConstituent(char: String) {
        clearSelection()
        suggestionJob?.cancel()
        val isSuggested = char == _uiState.value.suggestedWord && _uiState.value.suggestedWord != null
        _uiState.update {
            it.copy(
                query = char,
                isAutoSuggested = isSuggested,
                suggestionReason = if (isSuggested) it.suggestionReason else null
            )
        }
        executeSearch(char, skipDebounce = true)
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedWord = null,
                selectedCharReadings = emptyList(),
                containingCompounds = emptyList(),
                constituentCharacters = emptyList(),
                selectedTranslations = emptySet()
            )
        }
    }

    fun toggleTranslationForStudy(translation: String) {
        val word = _uiState.value.selectedWord ?: return
        viewModelScope.launch {
            val cleanTrans = translation.trim()
            if (cleanTrans.isEmpty()) return@launch

            val currentSelected = _uiState.value.selectedTranslations
            val isSelected = currentSelected.contains(cleanTrans)

            val newSelected = if (isSelected) {
                currentSelected - cleanTrans
            } else {
                currentSelected + cleanTrans
            }

            // Sync with local repository
            if (isSelected) {
                localVocabularyRepository.removeTranslation(word.chinese, word.pinyin, cleanTrans)
            } else {
                localVocabularyRepository.addTranslation(word.chinese, word.pinyin, cleanTrans)
            }

            // Ensure card is in Leitner study queue if selecting
            var isInStudy = _uiState.value.isInStudyQueue
            var box = _uiState.value.currentBox
            if (!isSelected && !isInStudy) {
                studyRepository.recordReviewGrade(word.chinese, ReviewGrade.AGAIN)
                val newState = studyRepository.getLeitnerState(word.chinese)
                isInStudy = true
                box = newState?.box ?: 0
            }

            _uiState.update {
                it.copy(
                    selectedTranslations = newSelected,
                    isInStudyQueue = isInStudy,
                    currentBox = box
                )
            }
        }
    }

    fun addCustomTranslation(customText: String) {
        val word = _uiState.value.selectedWord ?: return
        viewModelScope.launch {
            val cleanTrans = customText.trim()
            if (cleanTrans.isEmpty()) return@launch

            localVocabularyRepository.addTranslation(word.chinese, word.pinyin, cleanTrans)

            var isInStudy = _uiState.value.isInStudyQueue
            var box = _uiState.value.currentBox
            if (!isInStudy) {
                studyRepository.recordReviewGrade(word.chinese, ReviewGrade.AGAIN)
                val newState = studyRepository.getLeitnerState(word.chinese)
                isInStudy = true
                box = newState?.box ?: 0
            }

            val updatedEnglish = ArrayList((word.english + cleanTrans).distinct())
            val updatedWord = Vocabulary(
                chinese = word.chinese,
                pinyin = word.pinyin,
                english = updatedEnglish
            )
            val newSelected = _uiState.value.selectedTranslations + cleanTrans

            _uiState.update {
                it.copy(
                    selectedWord = updatedWord,
                    selectedTranslations = newSelected,
                    isInStudyQueue = isInStudy,
                    currentBox = box
                )
            }
        }
    }

    fun toggleAddToStudy(vocabulary: Vocabulary) {
        viewModelScope.launch {
            val hanzi = vocabulary.chinese
            val currentState = studyRepository.getLeitnerState(hanzi)
            val isCurrentlyStudying = _uiState.value.isInStudyQueue || currentState != null

            if (!isCurrentlyStudying) {
                // Determine translations: if some are already selected, use them; otherwise add all CEDICT translations
                val translationsToUse = if (_uiState.value.selectedTranslations.isNotEmpty()) {
                    _uiState.value.selectedTranslations.toList()
                } else {
                    vocabulary.english
                }

                localVocabularyRepository.addVocabulary(
                    Vocabulary(chinese = vocabulary.chinese, pinyin = vocabulary.pinyin, english = ArrayList(translationsToUse))
                )
                studyRepository.recordReviewGrade(hanzi, ReviewGrade.AGAIN)
                val newState = studyRepository.getLeitnerState(hanzi)

                _uiState.update {
                    it.copy(
                        isInStudyQueue = true,
                        currentBox = newState?.box ?: 0,
                        selectedTranslations = translationsToUse.toSet()
                    )
                }
            } else {
                // Remove from study and deselect all translations
                studyRepository.deleteRecord(hanzi)
                localVocabularyRepository.deleteWord(hanzi, vocabulary.pinyin)
                if (hanzi.codePointCount(0, hanzi.length) == 1) {
                    localVocabularyRepository.deleteCharacter(hanzi)
                }

                _uiState.update {
                    it.copy(
                        isInStudyQueue = false,
                        currentBox = null,
                        selectedTranslations = emptySet()
                    )
                }
            }
            loadHskStats()
        }
    }

    companion object {
        fun provideFactory(
            dictionaryRepository: VocabularyRepository,
            localVocabularyRepository: VocabularyRepository,
            studyRepository: StudyRepository,
            wordRecommendationEngine: WordRecommendationEngine = WordRecommendationEngine(dictionaryRepository)
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DictionaryViewModel(
                    dictionaryRepository,
                    localVocabularyRepository,
                    studyRepository,
                    wordRecommendationEngine = wordRecommendationEngine
                ) as T
            }
        }
    }
}
