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

enum class ActiveCardTab {
    WORDS,
    CHARACTERS
}

data class ActiveVocabularyItem(
    val vocabulary: Vocabulary,
    val leitnerState: LeitnerState?
)

data class ActiveCharacterItem(
    val character: String,
    val pinyins: List<String>,
    val containingWords: List<String>,
    val leitnerState: LeitnerState?
)

data class VocabularyListUiState(
    val currentTab: ActiveCardTab = ActiveCardTab.WORDS,
    val items: List<ActiveVocabularyItem> = emptyList(),
    val filteredItems: List<ActiveVocabularyItem> = emptyList(),
    val characterItems: List<ActiveCharacterItem> = emptyList(),
    val filteredCharacterItems: List<ActiveCharacterItem> = emptyList(),
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
    val errorMessage: String? = null,
    val minAvailableBox: Int = 0,
    val maxAvailableBox: Int = 0,
    val selectedBoxRange: IntRange? = null,
    val boxCounts: Map<Int, Int> = emptyMap()
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

    fun selectTab(tab: ActiveCardTab) {
        if (_uiState.value.currentTab == tab) return
        _uiState.update { state ->
            val boxes = if (tab == ActiveCardTab.WORDS) {
                state.items.map { it.leitnerState?.box ?: 0 }
            } else {
                state.characterItems.map { it.leitnerState?.box ?: 0 }
            }
            val minBox = boxes.minOrNull() ?: 0
            val maxBox = boxes.maxOrNull() ?: 0
            val boxCounts = boxes.groupingBy { it }.eachCount()

            state.copy(
                currentTab = tab,
                selectedBoxRange = null,
                minAvailableBox = minBox,
                maxAvailableBox = maxBox,
                boxCounts = boxCounts,
                filteredItems = filterWordList(state.items, state.query, null),
                filteredCharacterItems = filterCharacterList(state.characterItems, state.query, null)
            )
        }
    }

    fun loadActiveVocabulary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val localVocabs = localVocabularyRepository.getAllVocabulary()
                    .filter { it.english.isNotEmpty() }
                val allLeitner = studyRepository.getAllRecords()
                val leitnerMap = allLeitner.associateBy { it.hanzi }

                // 1. Word Items
                val wordItems = localVocabs.map { voc ->
                    ActiveVocabularyItem(
                        vocabulary = voc,
                        leitnerState = leitnerMap[voc.chinese]
                    )
                }.sortedWith(
                    compareByDescending<ActiveVocabularyItem> { it.leitnerState?.box ?: 0 }
                        .thenBy { it.vocabulary.pinyin }
                        .thenBy { it.vocabulary.chinese }
                )

                // 2. Character Items: Map which active words contain each character
                val wordMap = mutableMapOf<String, MutableList<String>>()
                for (voc in localVocabs) {
                    val word = voc.chinese
                    var i = 0
                    while (i < word.length) {
                        val cp = word.codePointAt(i)
                        val charStr = String(Character.toChars(cp))
                        val list = wordMap.getOrPut(charStr) { mutableListOf() }
                        if (word !in list) {
                            list.add(word)
                        }
                        i += Character.charCount(cp)
                    }
                }

                val charEntities = localVocabularyRepository.getAllCharPinyins()
                val charPinyinMap = charEntities.groupBy({ it.hanzi }, { it.pinyin })

                val allChars = mutableSetOf<String>()
                allChars.addAll(charPinyinMap.keys)
                allChars.addAll(wordMap.keys)
                for (record in allLeitner) {
                    if (record.hanzi.codePointCount(0, record.hanzi.length) == 1) {
                        allChars.add(record.hanzi)
                    }
                }

                val characterItems = allChars.map { charStr ->
                    var pinyins = charPinyinMap[charStr]?.distinct() ?: emptyList()
                    if (pinyins.isEmpty()) {
                        pinyins = dictionaryRepository.getCharPinyinsForHanzi(charStr).map { it.pinyin }.distinct()
                    }
                    if (pinyins.isEmpty()) {
                        val voc = localVocabs.find { it.chinese == charStr }
                        if (voc != null && voc.pinyin.isNotEmpty()) {
                            pinyins = listOf(voc.pinyin)
                        }
                    }
                    val containingWords = wordMap[charStr] ?: emptyList()
                    val leitnerState = leitnerMap[charStr]

                    ActiveCharacterItem(
                        character = charStr,
                        pinyins = pinyins,
                        containingWords = containingWords,
                        leitnerState = leitnerState
                    )
                }.sortedWith(
                    compareByDescending<ActiveCharacterItem> { it.leitnerState?.box ?: 0 }
                        .thenBy { it.pinyins.firstOrNull() ?: "" }
                        .thenBy { it.character }
                )

                val activeTab = _uiState.value.currentTab
                val currentBoxes = if (activeTab == ActiveCardTab.WORDS) {
                    wordItems.map { it.leitnerState?.box ?: 0 }
                } else {
                    characterItems.map { it.leitnerState?.box ?: 0 }
                }
                val minBox = currentBoxes.minOrNull() ?: 0
                val maxBox = currentBoxes.maxOrNull() ?: 0
                val boxCounts = currentBoxes.groupingBy { it }.eachCount()

                val currentRange = _uiState.value.selectedBoxRange
                val newRange = if (currentRange != null && currentBoxes.isNotEmpty()) {
                    val clampedStart = currentRange.first.coerceIn(minBox, maxBox)
                    val clampedEnd = currentRange.last.coerceIn(minBox, maxBox)
                    if (clampedStart <= clampedEnd && (clampedStart != minBox || clampedEnd != maxBox)) {
                        clampedStart..clampedEnd
                    } else {
                        null
                    }
                } else {
                    null
                }

                _uiState.update { state ->
                    state.copy(
                        items = wordItems,
                        characterItems = characterItems,
                        minAvailableBox = minBox,
                        maxAvailableBox = maxBox,
                        boxCounts = boxCounts,
                        selectedBoxRange = newRange,
                        filteredItems = filterWordList(wordItems, state.query, newRange),
                        filteredCharacterItems = filterCharacterList(characterItems, state.query, newRange),
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
                filteredItems = filterWordList(state.items, newQuery, state.selectedBoxRange),
                filteredCharacterItems = filterCharacterList(state.characterItems, newQuery, state.selectedBoxRange)
            )
        }
    }

    fun onBoxRangeChanged(range: IntRange?) {
        _uiState.update { state ->
            val effectiveRange = if (range != null &&
                (range.first <= state.minAvailableBox && range.last >= state.maxAvailableBox)
            ) {
                null
            } else {
                range
            }
            state.copy(
                selectedBoxRange = effectiveRange,
                filteredItems = filterWordList(state.items, state.query, effectiveRange),
                filteredCharacterItems = filterCharacterList(state.characterItems, state.query, effectiveRange)
            )
        }
    }

    private fun filterWordList(
        items: List<ActiveVocabularyItem>,
        query: String,
        boxRange: IntRange?
    ): List<ActiveVocabularyItem> {
        val trimmed = query.trim()
        val lowerQuery = trimmed.lowercase()
        val hasQuery = trimmed.isNotEmpty()

        return items.filter { item ->
            val box = item.leitnerState?.box ?: 0
            if (boxRange != null && box !in boxRange) {
                return@filter false
            }

            if (!hasQuery) {
                return@filter true
            }

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

    private fun filterCharacterList(
        items: List<ActiveCharacterItem>,
        query: String,
        boxRange: IntRange?
    ): List<ActiveCharacterItem> {
        val trimmed = query.trim()
        val lowerQuery = trimmed.lowercase()
        val hasQuery = trimmed.isNotEmpty()

        return items.filter { item ->
            val box = item.leitnerState?.box ?: 0
            if (boxRange != null && box !in boxRange) {
                return@filter false
            }

            if (!hasQuery) {
                return@filter true
            }

            val ch = item.character
            val pinyins = item.pinyins.map { it.lowercase() }
            val pinyinsNoTone = pinyins.map { p -> p.filter { !it.isDigit() } }
            val words = item.containingWords

            ch.contains(trimmed) ||
                pinyins.any { it.contains(lowerQuery) } ||
                pinyinsNoTone.any { it.contains(lowerQuery) } ||
                words.any { it.contains(trimmed) }
        }
    }

    fun selectCharacter(char: String) {
        selectCharacterFromConstituent(char)
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

            val isStudying = localMatching != null && localMatching.english.isNotEmpty()
            val selectedTranslations = if (isStudying) {
                localMatching!!.english.toSet()
            } else {
                emptySet()
            }

            // Merge dictionary definitions and local custom translations so all options are visible
            val dictVocabs = dictionaryRepository.getVocabularyForChinese(hanzi)
            val dictMatching = dictVocabs.find { it.pinyin == vocabulary.pinyin } ?: dictVocabs.firstOrNull()
            val mergedEnglish = ArrayList(
                ((dictMatching?.english ?: emptyList()) + (localMatching?.english ?: emptyList()) + vocabulary.english).distinct()
            )
            val displayVoc = vocabulary.copy(
                pinyin = vocabulary.pinyin.ifEmpty { dictMatching?.pinyin ?: "" },
                english = mergedEnglish
            )

            _uiState.update {
                it.copy(
                    selectedItem = item,
                    selectedWord = displayVoc,
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
        viewModelScope.launch {
            val vocList = dictionaryRepository.getVocabularyForChinese(char).ifEmpty {
                localVocabularyRepository.getVocabularyForChinese(char)
            }
            val first = vocList.firstOrNull() ?: run {
                val pinyin = dictionaryRepository.getCharPinyinsForHanzi(char).firstOrNull()?.pinyin
                    ?: localVocabularyRepository.getCharPinyinsForHanzi(char).firstOrNull()?.pinyin
                    ?: ""
                Vocabulary(chinese = char, pinyin = pinyin, english = arrayListOf())
            }
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
