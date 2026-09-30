package com.matthieu.zi_app.ui.dictionary

import com.matthieu.zi_app.Vocabulary

data class DictionaryUiState(
    val query: String = "",
    val results: List<Vocabulary> = emptyList(),
    val selectedWord: Vocabulary? = null,
    val selectedCharReadings: List<Vocabulary> = emptyList(),
    val containingCompounds: List<Vocabulary> = emptyList(),
    val constituentCharacters: List<String> = emptyList(),
    val isInStudyQueue: Boolean = false,
    val currentBox: Int? = null,
    val selectedTranslations: Set<String> = emptySet(),
    val isSearching: Boolean = false,
    val errorMessage: String? = null
)
