package com.matthieu.zi_app.ui.hsk

import com.matthieu.zi_app.Vocabulary

data class HskBrowserUiState(
    val selectedLevel: Int = 1,
    val selectedFilter: HskFilter = HskFilter.ALL,
    val searchQuery: String = "",
    val levelStats: Map<Int, HskLevelProgress> = emptyMap(),
    val words: List<HskWordEntry> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,

    // Bottom Sheet Detail State (for inspecting words/characters)
    val selectedWord: Vocabulary? = null,
    val selectedCharReadings: List<Vocabulary> = emptyList(),
    val containingCompounds: List<Vocabulary> = emptyList(),
    val constituentCharacters: List<String> = emptyList(),
    val isInStudyQueue: Boolean = false,
    val currentBox: Int? = null,
    val selectedTranslations: Set<String> = emptySet()
)

