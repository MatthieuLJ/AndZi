package com.matthieu.zi_app.ui.vocabulary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.matthieu.zi_app.LeitnerState
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import com.matthieu.zi_app.domain.srs.ReviewGrade
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActiveVocabularyItem(
    val vocabulary: Vocabulary,
    val leitnerState: LeitnerState?
)

data class VocabularyListUiState(
    val items: List<ActiveVocabularyItem> = emptyList(),
    val filteredItems: List<ActiveVocabularyItem> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = false,
    val selectedItem: ActiveVocabularyItem? = null,
    val selectedWord: Vocabulary? = null,
    val selectedCharReadings: List<Vocabulary> = emptyList(),
    val containingCompounds: List<Vocabulary> = emptyList(),
    val constituentCharacters: List<String> = emptyList(),
    val selectedTranslations: Set<String> = emptySet(),
    val isInStudyQueue: Boolean = false,
    val currentBox: Int? = null,
    val errorMessage: String? = null
)

class VocabularyListViewModel(
    private val localVocabularyRepository: VocabularyRepository,
    private val dictionaryRepository: VocabularyRepository,
    private val studyRepository: StudyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VocabularyListUiState())
    val uiState: StateFlow<VocabularyListUiState> = _uiState.asStateFlow()

    private var filterJob: Job? = null

    init {
        loadActiveVocabulary()
    }

    fun loadActiveVocabulary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val localVocabs = localVocabularyRepository.getAllVocabulary()
                    .filter { it.english.isNotEmpty() }
                val allLeitner = studyRepository.getAllRecords()
                val leitnerMap = allLeitner.associateBy { it.hanzi }

                val items = localVocabs.map { voc ->
                    ActiveVocabularyItem(
                        vocabulary = voc,
                        leitnerState = leitnerMap[voc.chinese]
                    )
                }.sortedWith(
                    compareByDescending<ActiveVocabularyItem> { it.leitnerState?.box ?: 0 }
                        .thenBy { it.vocabulary.pinyin }
                        .thenBy { it.vocabulary.chinese }
                )

                _uiState.update { state ->
                    state.copy(
                        items = items,
                        filteredItems = filterList(items, state.query),
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Failed to load active cards"
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _uiState.update { state ->
            state.copy(
                query = newQuery,
                filteredItems = filterList(state.items, newQuery)
            )
        }
    }

    private fun filterList(items: List<ActiveVocabularyItem>, query: String): List<ActiveVocabularyItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return items

        val lowerQuery = trimmed.lowercase()
        return items.filter { item ->
            val ch = item.vocabulary.chinese
            val pi = item.vocabulary.pinyin.lowercase()
            val piNoTone = pi.filter { !it.isDigit() }
            val en = item.vocabulary.english

            ch.contains(trimmed) ||
                pi.contains(lowerQuery) ||
                piNoTone.contains(lowerQuery) ||
                en.any { it.contains(lowerQuery, ignoreCase = true) }
        }
    }

    fun selectItem(item: ActiveVocabularyItem) {
        viewModelScope.launch {
            val vocabulary = item.vocabulary
            val hanzi = vocabulary.chinese
            val charCount = hanzi.codePointCount(0, hanzi.length)

            // Polyphonic readings if single character
            val polyReadings = if (charCount == 1) {
                dictionaryRepository.getVocabularyForChinese(hanzi).ifEmpty {
                    localVocabularyRepository.getVocabularyForChinese(hanzi)
                }
            } else emptyList()

            // Containing compound words
            val containingCompounds = if (charCount == 1) {
                dictionaryRepository.getWordsContainingCharacter(hanzi).take(30).ifEmpty {
                    localVocabularyRepository.getWordsContainingCharacter(hanzi).take(30)
                }
            } else emptyList()

            // Constituent single characters if compound word
            val constituents = mutableListOf<String>()
            if (charCount > 1) {
                for (i in 0 until charCount) {
                    val codePoint = hanzi.codePointAt(i)
                    constituents.add(String(Character.toChars(codePoint)))
                }
            }

            val leitnerState = studyRepository.getLeitnerState(hanzi)

            // Local translations
            val localVocabs = localVocabularyRepository.getVocabularyForChinese(hanzi)
            val localMatching = localVocabs.find { it.pinyin == vocabulary.pinyin } ?: localVocabs.firstOrNull()

            val selectedTranslations = if (localMatching != null && localMatching.english.isNotEmpty()) {
                localMatching.english.toSet()
            } else if (vocabulary.english.isNotEmpty()) {
                vocabulary.english.toSet()
            } else {
                emptySet()
            }

            // Merge dictionary definitions and local custom translations so all options are visible
            val dictVocabs = dictionaryRepository.getVocabularyForChinese(hanzi)
            val dictMatching = dictVocabs.find { it.pinyin == vocabulary.pinyin } ?: dictVocabs.firstOrNull()
            val mergedEnglish = ArrayList(
                ((dictMatching?.english ?: emptyList()) + (localMatching?.english ?: emptyList()) + vocabulary.english).distinct()
            )
            val displayVoc = vocabulary.copy(english = mergedEnglish)

            _uiState.update {
                it.copy(
                    selectedItem = item,
                    selectedWord = displayVoc,
                    selectedCharReadings = polyReadings,
                    containingCompounds = containingCompounds,
                    constituentCharacters = constituents,
                    isInStudyQueue = leitnerState != null,
                    currentBox = leitnerState?.box,
                    selectedTranslations = selectedTranslations
                )
            }
        }
    }

    fun selectCharacterFromConstituent(char: String) {
        viewModelScope.launch {
            val vocList = dictionaryRepository.getVocabularyForChinese(char).ifEmpty {
                localVocabularyRepository.getVocabularyForChinese(char)
            }
            val first = vocList.firstOrNull() ?: Vocabulary(chinese = char, pinyin = "", english = arrayListOf())
            val state = studyRepository.getLeitnerState(char)
            selectItem(ActiveVocabularyItem(vocabulary = first, leitnerState = state))
        }
    }

    fun selectWordFromCompound(compound: Vocabulary) {
        viewModelScope.launch {
            val state = studyRepository.getLeitnerState(compound.chinese)
            selectItem(ActiveVocabularyItem(vocabulary = compound, leitnerState = state))
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedItem = null,
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
            loadActiveVocabulary()
        }
    }

    /**
     * Manually adds a custom English translation typed by the user for the currently selected word,
     * persists it in the local study database, and includes it in the active study translations.
     */
    fun addCustomTranslation(customText: String) {
        val word = _uiState.value.selectedWord ?: return
        val cleanTrans = customText.trim()
        if (cleanTrans.isEmpty()) return

        viewModelScope.launch {
            localVocabularyRepository.addTranslation(word.chinese, word.pinyin, cleanTrans)

            var isInStudy = _uiState.value.isInStudyQueue
            var box = _uiState.value.currentBox
            if (!isInStudy) {
                studyRepository.recordReviewGrade(word.chinese, ReviewGrade.AGAIN)
                val newState = studyRepository.getLeitnerState(word.chinese)
                isInStudy = true
                box = newState?.box ?: 0
            }

            val updatedEnglish = if (word.english.contains(cleanTrans)) {
                word.english
            } else {
                ArrayList(word.english + cleanTrans)
            }
            val updatedWord = word.copy(english = updatedEnglish)
            val newSelected = _uiState.value.selectedTranslations + cleanTrans

            _uiState.update {
                it.copy(
                    selectedWord = updatedWord,
                    selectedTranslations = newSelected,
                    isInStudyQueue = isInStudy,
                    currentBox = box
                )
            }
            loadActiveVocabulary()
        }
    }

    fun toggleAddToStudy(vocabulary: Vocabulary) {
        viewModelScope.launch {
            val hanzi = vocabulary.chinese
            val currentState = studyRepository.getLeitnerState(hanzi)
            val isCurrentlyStudying = _uiState.value.isInStudyQueue || currentState != null

            if (!isCurrentlyStudying) {
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
                removeFromStudy(vocabulary)
            }
            loadActiveVocabulary()
        }
    }

    fun resetSrsScore(vocabulary: Vocabulary) {
        viewModelScope.launch {
            val hanzi = vocabulary.chinese
            studyRepository.restartStudy(hanzi)
            _uiState.update {
                it.copy(
                    isInStudyQueue = true,
                    currentBox = 0
                )
            }
            loadActiveVocabulary()
        }
    }

    fun removeFromStudy(vocabulary: Vocabulary) {
        viewModelScope.launch {
            val hanzi = vocabulary.chinese
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
            loadActiveVocabulary()
        }
    }

    companion object {
        fun provideFactory(
            localVocabularyRepository: VocabularyRepository,
            dictionaryRepository: VocabularyRepository,
            studyRepository: StudyRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VocabularyListViewModel(
                    localVocabularyRepository,
                    dictionaryRepository,
                    studyRepository
                ) as T
            }
        }
    }
}
