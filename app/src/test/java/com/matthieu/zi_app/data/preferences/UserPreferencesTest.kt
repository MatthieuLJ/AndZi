package com.matthieu.zi_app.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class UserPreferencesTest {

    private lateinit var context: Context
    private lateinit var userPreferences: UserPreferences

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences(UserPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        userPreferences = UserPreferences(context)
    }

    private fun dateToMillis(date: LocalDate): Long {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1000L
    }

    @Test
    fun testInitialStreakState() {
        assertTrue(userPreferences.hasNoRecordedStudy())
        assertEquals(0, userPreferences.getStreakDays())
        assertEquals(0, userPreferences.streakDays.value)
    }

    @Test
    fun testStreakIncrementsAcrossConsecutiveDays() {
        val baseDate = LocalDate.of(2026, 10, 20)
        val day1 = dateToMillis(baseDate)
        val day2 = dateToMillis(baseDate.plusDays(1))
        val day3 = dateToMillis(baseDate.plusDays(2))

        // Day 1: First study
        val s1 = userPreferences.recordStudySession(day1)
        assertEquals(1, s1)
        assertEquals(1, userPreferences.getStreakDays(day1))
        assertFalse(userPreferences.hasNoRecordedStudy())

        // Multiple sessions on Day 1 should not increment streak further
        val s1Again = userPreferences.recordStudySession(day1 + 3600000L)
        assertEquals(1, s1Again)
        assertEquals(1, userPreferences.getStreakDays(day1 + 3600000L))

        // Day 2 before studying: streak is still active from yesterday
        assertEquals(1, userPreferences.getStreakDays(day2))

        // Day 2: Study done
        val s2 = userPreferences.recordStudySession(day2)
        assertEquals(2, s2)
        assertEquals(2, userPreferences.getStreakDays(day2))

        // Day 3 before studying: streak is 2
        assertEquals(2, userPreferences.getStreakDays(day3))

        // Day 3: Study done
        val s3 = userPreferences.recordStudySession(day3)
        assertEquals(3, s3)
        assertEquals(3, userPreferences.getStreakDays(day3))
    }

    @Test
    fun testStreakBreaksWhenDayIsSkipped() {
        val baseDate = LocalDate.of(2026, 10, 20)
        val day1 = dateToMillis(baseDate)
        val day2 = dateToMillis(baseDate.plusDays(1))
        val day4 = dateToMillis(baseDate.plusDays(3)) // Skipped day 3!

        userPreferences.recordStudySession(day1)
        userPreferences.recordStudySession(day2)
        assertEquals(2, userPreferences.getStreakDays(day2))

        // Day 4 (2 days after day 2): Streak is broken
        assertEquals(0, userPreferences.getStreakDays(day4))

        // Studying on Day 4 restarts streak at 1
        val s4 = userPreferences.recordStudySession(day4)
        assertEquals(1, s4)
        assertEquals(1, userPreferences.getStreakDays(day4))
    }

    @Test
    fun testResetStreak() {
        val baseDate = LocalDate.of(2026, 10, 20)
        val day1 = dateToMillis(baseDate)

        userPreferences.recordStudySession(day1)
        assertEquals(1, userPreferences.getStreakDays(day1))

        userPreferences.resetStreak()
        assertEquals(0, userPreferences.getStreakDays(day1))
        assertTrue(userPreferences.hasNoRecordedStudy())
    }
}

