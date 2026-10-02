package com.matthieu.zi_app.domain.recommendation

data class WordRecommendation(
    val word: String,
    val reason: SuggestionReason
)

sealed interface SuggestionReason {
    fun formatDisplay(): String

    /**
     * Composed entirely from characters already known/studied by the user.
     */
    data class ComposedFromKnown(val constituentChars: List<String>, val hskLevel: Int?) : SuggestionReason {
        override fun formatDisplay(): String {
            val chars = constituentChars.joinToString(", ")
            val hskPart = if (hskLevel != null && hskLevel in 1..9) " • HSK $hskLevel" else ""
            return "Composed from known characters: $chars$hskPart"
        }
    }

    /**
     * Composed of 1 known character and 1 new character.
     */
    data class PartiallyComposed(val knownChar: String, val newChar: String, val hskLevel: Int?) : SuggestionReason {
        override fun formatDisplay(): String {
            val hskPart = if (hskLevel != null && hskLevel in 1..9) " • HSK $hskLevel" else ""
            return "Composed of known '$knownChar' + new '$newChar'$hskPart"
        }
    }

    /**
     * Foundational character or word from the HSK curriculum.
     */
    data class HskCurriculum(val level: Int, val isChar: Boolean) : SuggestionReason {
        override fun formatDisplay(): String {
            return if (isChar) {
                "Foundational HSK $level character"
            } else {
                "Foundational HSK $level word"
            }
        }
    }

    /**
     * Common high-frequency word or character.
     */
    data class HighFrequency(val rank: Int) : SuggestionReason {
        override fun formatDisplay(): String {
            return if (rank <= 100) {
                "High frequency vocabulary (Top 100)"
            } else if (rank <= 500) {
                "High frequency vocabulary (Top 500)"
            } else {
                "Common vocabulary (Top $rank)"
            }
        }
    }
}
