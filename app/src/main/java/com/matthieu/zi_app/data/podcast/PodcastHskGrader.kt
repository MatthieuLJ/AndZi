package com.matthieu.zi_app.data.podcast

/**
 * Calculates pedagogical difficulty levels (HSK 1-6) and mastery metrics for podcast stories.
 */
object PodcastHskGrader {

    /**
     * Calculates the target HSK level for an episode from its vocabulary HSK levels.
     * Takes the 80th-percentile HSK level among HSK-classified words (levels 1..6).
     *
     * Example: If 80% of an episode's HSK vocabulary is at or below HSK 3, the episode is
     * graded as HSK 3, meaning a learner at HSK 3 can comprehend >= 80% of the core vocabulary.
     *
     * Returns 0 if no words in the episode have an HSK classification.
     */
    fun computeTargetHskLevel(vocabLevels: Collection<Int>): Int {
        val validLevels = vocabLevels.filter { it in 1..6 }.sorted()
        if (validLevels.isEmpty()) return 0
        val p80Index = (validLevels.size * 0.80).toInt().coerceIn(0, validLevels.lastIndex)
        return validLevels[p80Index]
    }
}
