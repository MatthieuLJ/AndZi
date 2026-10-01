package com.matthieu.zi_app.domain.srs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryForecastCalculatorTest {

    @Test
    fun testEmptyStates() {
        val health = MemoryForecastCalculator.calculateMemoryHealth(emptyList())
        assertEquals(0, health.totalActiveWords)
        assertEquals(0f, health.totalRetainedWords, 1e-4f)

        val forecast = MemoryForecastCalculator.calculateForecast(emptyList())
        assertEquals(0, forecast.totalCards)
        assertTrue(forecast.points.isEmpty())
    }

    @Test
    fun testMemoryHealthCalculation() {
        val now = 1_000_000_000L
        val dayMillis = 24L * 3600 * 1000L

        // Card 1: Freshly reviewed today (high stability) -> Strong (>90%)
        val card1 = ReviewState(
            cardId = "好",
            box = 3,
            nextReviewTimestamp = now + (7 * dayMillis),
            dateAdded = now - (14 * dayMillis),
            customData = mapOf(
                EbisuSpacedRepetitionEngine.KEY_ALPHA to "4.0",
                EbisuSpacedRepetitionEngine.KEY_BETA to "4.0",
                EbisuSpacedRepetitionEngine.KEY_T0 to (14 * dayMillis.toDouble()).toString(),
                EbisuSpacedRepetitionEngine.KEY_LAST_REVIEW to now.toString()
            )
        )

        // Card 2: Box 0 (unlearned / retry) -> Due
        val card2 = ReviewState(
            cardId = "难",
            box = 0,
            nextReviewTimestamp = now,
            dateAdded = now
        )

        val health = MemoryForecastCalculator.calculateMemoryHealth(listOf(card1, card2), now)
        assertEquals(2, health.totalActiveWords)
        assertEquals(1, health.strongCount)
        assertEquals(1, health.dueCount)
        assertEquals(1.0f, health.totalRetainedWords, 0.01f)
        assertEquals(50.0f, health.retentionPercentage, 0.1f)
    }

    @Test
    fun testForecastCurveDecayAndDueWorkload() {
        val now = 1_000_000_000L
        val dayMillis = 24L * 3600 * 1000L

        val card1 = ReviewState(
            cardId = "好",
            box = 2,
            nextReviewTimestamp = now + (3 * dayMillis),
            dateAdded = now - (7 * dayMillis),
            customData = mapOf(
                EbisuSpacedRepetitionEngine.KEY_ALPHA to "4.0",
                EbisuSpacedRepetitionEngine.KEY_BETA to "4.0",
                EbisuSpacedRepetitionEngine.KEY_T0 to (5 * dayMillis.toDouble()).toString(),
                EbisuSpacedRepetitionEngine.KEY_LAST_REVIEW to now.toString()
            )
        )

        val card2 = ReviewState(
            cardId = "学",
            box = 4,
            nextReviewTimestamp = now + (10 * dayMillis),
            dateAdded = now - (20 * dayMillis),
            customData = mapOf(
                EbisuSpacedRepetitionEngine.KEY_ALPHA to "4.0",
                EbisuSpacedRepetitionEngine.KEY_BETA to "4.0",
                EbisuSpacedRepetitionEngine.KEY_T0 to (30 * dayMillis.toDouble()).toString(),
                EbisuSpacedRepetitionEngine.KEY_LAST_REVIEW to now.toString()
            )
        )

        val forecast = MemoryForecastCalculator.calculateForecast(
            states = listOf(card1, card2),
            now = now,
            maxHorizonDays = 90
        )

        assertEquals(2, forecast.totalCards)
        assertFalse(forecast.points.isEmpty())
        assertFalse(forecast.dueBuckets.isEmpty())

        // Initial point on forecast should have higher retention than point at 90 days
        val firstPoint = forecast.points.first()
        val lastPoint = forecast.points.last()
        assertTrue(firstPoint.retentionPercentage > lastPoint.retentionPercentage)
    }
}
