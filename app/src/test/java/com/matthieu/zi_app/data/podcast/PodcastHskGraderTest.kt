package com.matthieu.zi_app.data.podcast

import org.junit.Assert.assertEquals
import org.junit.Test

class PodcastHskGraderTest {

    @Test
    fun testEmptyVocabReturnsZero() {
        assertEquals(0, PodcastHskGrader.computeTargetHskLevel(emptyList()))
        assertEquals(0, PodcastHskGrader.computeTargetHskLevel(listOf(0, 0, 0)))
    }

    @Test
    fun testUniformHskLevel() {
        val levels = listOf(2, 2, 2, 2, 2)
        assertEquals(2, PodcastHskGrader.computeTargetHskLevel(levels))
    }

    @Test
    fun testMixedHskLevels80thPercentile() {
        // 10 words:
        // 5x HSK 1, 3x HSK 2, 2x HSK 3
        // Sorted: [1, 1, 1, 1, 1, 2, 2, 2, 3, 3]
        // 80% index = (10 * 0.80).toInt() = 8
        // Index 8 is 3
        val levels = listOf(1, 1, 1, 1, 1, 2, 2, 2, 3, 3)
        assertEquals(3, PodcastHskGrader.computeTargetHskLevel(levels))

        // 10 words:
        // 8x HSK 1, 2x HSK 3
        // Sorted: [1, 1, 1, 1, 1, 1, 1, 1, 3, 3]
        // Index 8 is 3. But 9x HSK 1, 1x HSK 3:
        // Sorted: [1, 1, 1, 1, 1, 1, 1, 1, 1, 3]
        // Index 8 is 1!
        val mostlyLevel1 = listOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 3)
        assertEquals(1, PodcastHskGrader.computeTargetHskLevel(mostlyLevel1))
    }

    @Test
    fun testIgnoresNonHskZeros() {
        // 0s (unclassified) are ignored when determining HSK target level
        val levelsWithZeros = listOf(0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 0)
        // 8 valid levels: [1, 1, 1, 1, 2, 2, 2, 2]
        // Index = (8 * 0.8) = 6 -> 2
        assertEquals(2, PodcastHskGrader.computeTargetHskLevel(levelsWithZeros))
    }
}
