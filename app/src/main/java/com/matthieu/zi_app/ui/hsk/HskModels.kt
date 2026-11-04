package com.matthieu.zi_app.ui.hsk

import com.matthieu.zi_app.Vocabulary

enum class HskFilter(val label: String) {
    ALL("All"),
    UNLEARNED("Unlearned"),
    STUDIED("Studied")
}

data class HskLevelProgress(
    val level: Int,
    val totalWords: Int,
    val studiedWords: Int
) {
    val unlearnedWords: Int get() = (totalWords - studiedWords).coerceAtLeast(0)
    val percentage: Float get() = if (totalWords > 0) studiedWords.toFloat() / totalWords.toFloat() else 0f
    val percentageInt: Int get() = (percentage * 100f).toInt()
}

data class HskWordEntry(
    val vocabulary: Vocabulary,
    val hskLevel: Int,
    val isStudied: Boolean,
    val leitnerBox: Int? = null
)

