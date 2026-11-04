package com.matthieu.zi_app.ui.hsk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import com.matthieu.zi_app.domain.srs.ReviewGrade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HskBrowserViewModel(
    private val dictionaryRepository: VocabularyRepository,
    private val localVocabularyRepository: VocabularyRepository,
    private val studyRepository: StudyRepository,
    initialLevel: Int = 1
) : ViewModel() {

    private val _uiState = MutableStateFlow(HskBrowserUiState(selectedLevel = initialLevel))
    val uiState: StateFlow<HskBrowserUiState> = _uiState.asStateFlow()

    private var allLevelWords: List<HskWordEntry> = emptyList()

    init {
        loadData(initialLevel)
    }

    fun selectLevel(level: Int) {
        if (_uiState.value.selectedLevel == level && allLevelWords.isNotEmpty()) return
        _uiState.update { it.copy(selectedLevel = level, searchQuery = "") }
        loadLevelWords(level)
    }

    fun selectFilter(filter: HskFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
        applyFilters()
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    private fun loadData(level: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            refreshLevelStats()
            loadLevelWordsInternal(level)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun loadLevelWords(level: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            loadLevelWordsInternal(level)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun refreshLevelStats() = withContext(Dispatchers.IO) {
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
                HskLevelProgress(level = lvl, totalWords = total, studiedWords = studied)
            }

            _uiState.update { it.copy(levelStats = stats) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun loadLevelWordsInternal(level: Int) = withContext(Dispatchers.IO) {
        try {
            val rawVocab = dictionaryRepository.getVocabularyForHskLevel(level)
            val studyRecords = studyRepository.getAllRecords().associateBy { it.hanzi }

            allLevelWords = rawVocab.map { vocab ->
                val record = studyRecords[vocab.chinese]
                HskWordEntry(
                    vocabulary = vocab,
                    hskLevel = level,
                    isStudied = record != null,
                    leitnerBox = record?.box
                )
            }
            applyFiltersInternal()
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Failed to load HSK $level vocabulary: ${e.message}") }
        }
    }

    private fun applyFilters() {
        applyFiltersInternal()
    }

    private fun applyFiltersInternal() {
        val filter = _uiState.value.selectedFilter
        val query = _uiState.value.searchQuery.trim().lowercase()

        val filtered = allLevelWords.filter { entry ->
            // Filter by study status
            val statusMatches = when (filter) {
                HskFilter.ALL -> true
                HskFilter.UNLEARNED -> !entry.isStudied
                HskFilter.STUDIED -> entry.isStudied
            }
            if (!statusMatches) return@filter false

            // Filter by search query
            if (query.isEmpty()) return@filter true

            val word = entry.vocabulary
            word.chinese.contains(query) ||
                word.pinyin.lowercase().contains(query) ||
                word.english.any { it.lowercase().contains(query) }
        }

        _uiState.update { it.copy(words = filtered) }
    }

    fun toggleStudy(entry: HskWordEntry) {
        viewModelScope.launch {
            val vocab = entry.vocabulary
            val hanzi = vocab.chinese
            val currentlyStudied = entry.isStudied

            if (!currentlyStudied) {
                // Add to study deck
                localVocabularyRepository.addVocabulary(vocab)
                studyRepository.recordReviewGrade(hanzi, ReviewGrade.AGAIN)
                val newState = studyRepository.getLeitnerState(hanzi)
                val newBox = newState?.box ?: 0

                allLevelWords = allLevelWords.map {
                    if (it.vocabulary.chinese == hanzi) {
                        it.copy(isStudied = true, leitnerBox = newBox)
                    } else it
                }
            } else {
                // Remove from study deck
                studyRepository.deleteRecord(hanzi)
                localVocabularyRepository.deleteWord(hanzi, vocab.pinyin)
                if (hanzi.codePointCount(0, hanzi.length) == 1) {
                    localVocabularyRepository.deleteCharacter(hanzi)
                }

                allLevelWords = allLevelWords.map {
                    if (it.vocabulary.chinese == hanzi) {
                        it.copy(isStudied = false, leitnerBox = null)
                    } else it
                }
            }

            applyFiltersInternal()
            refreshLevelStats()

            // If this word is currently opened in bottom sheet, update bottom sheet state too
            if (_uiState.value.selectedWord?.chinese == hanzi) {
                _uiState.update {
                    it.copy(
                        isInStudyQueue = !currentlyStudied,
                        currentBox = if (!currentlyStudied) 0 else null
                    )
                }
            }
        }
    }

    fun selectWord(vocabulary: Vocabulary) {
        viewModelScope.launch {
            val hanzi = vocabulary.chinese
            val polyReadings = if (hanzi.codePointCount(0, hanzi.length) == 1) {
                dictionaryRepository.getVocabularyForChinese(hanzi)
            } else {
                emptyList()
            }

            val containingCompounds = if (hanzi.codePointCount(0, hanzi.length) == 1) {
                dictionaryRepository.getWordsContainingCharacter(hanzi).take(15)
            } else {
                emptyList()
            }

            val constituents = if (hanzi.length > 1) {
                val chars = mutableListOf<String>()
                var offset = 0
                while (offset < hanzi.length) {
                    val codePoint = hanzi.codePointAt(offset)
                    val charStr = String(Character.toChars(codePoint))
                    chars.add(charStr)
                    offset += Character.charCount(codePoint)
                }
                chars
            } else {
                emptyList()
            }

            val leitnerState = studyRepository.getLeitnerState(hanzi)
            val localVocabs = localVocabularyRepository.getVocabularyForChinese(hanzi)
            val localMatching = localVocabs.find { it.pinyin == vocabulary.pinyin } ?: localVocabs.firstOrNull()
            val isStudying = localMatching != null && localMatching.english.isNotEmpty()
            val selectedTranslations = if (isStudying) localMatching!!.english.toSet() else emptySet()
            val mergedEnglish = ArrayList((vocabulary.english + (localMatching?.english ?: emptyList())).distinct())

            _uiState.update {
                it.copy(
                    selectedWord = Vocabulary(chinese = hanzi, pinyin = vocabulary.pinyin, english = mergedEnglish),
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

    fun clearWordSelection() {
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

            val isSelected = cleanTrans in _uiState.value.selectedTranslations
            if (isSelected) {
                localVocabularyRepository.removeTranslation(word.chinese, word.pinyin, cleanTrans)
            } else {
                localVocabularyRepository.addTranslation(word.chinese, word.pinyin, cleanTrans)
            }

            val localVocabs = localVocabularyRepository.getVocabularyForChinese(word.chinese)
            val localMatching = localVocabs.find { it.pinyin == word.pinyin } ?: localVocabs.firstOrNull()
            val newSelected = localMatching?.english?.toSet() ?: emptySet()

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

            // Sync with word list
            allLevelWords = allLevelWords.map {
                if (it.vocabulary.chinese == word.chinese) {
                    it.copy(isStudied = isInStudy, leitnerBox = box)
                } else it
            }
            applyFiltersInternal()
            refreshLevelStats()
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
            val updatedWord = Vocabulary(chinese = word.chinese, pinyin = word.pinyin, english = updatedEnglish)
            val newSelected = _uiState.value.selectedTranslations + cleanTrans

            _uiState.update {
                it.copy(
                    selectedWord = updatedWord,
                    selectedTranslations = newSelected,
                    isInStudyQueue = isInStudy,
                    currentBox = box
                )
            }

            allLevelWords = allLevelWords.map {
                if (it.vocabulary.chinese == word.chinese) {
                    it.copy(isStudied = isInStudy, leitnerBox = box)
                } else it
            }
            applyFiltersInternal()
            refreshLevelStats()
        }
    }

    companion object {
        fun provideFactory(
            dictionaryRepository: VocabularyRepository,
            localVocabularyRepository: VocabularyRepository,
            studyRepository: StudyRepository,
            initialLevel: Int = 1
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HskBrowserViewModel(
                    dictionaryRepository = dictionaryRepository,
                    localVocabularyRepository = localVocabularyRepository,
                    studyRepository = studyRepository,
                    initialLevel = initialLevel
                ) as T
            }
        }
    }
}
