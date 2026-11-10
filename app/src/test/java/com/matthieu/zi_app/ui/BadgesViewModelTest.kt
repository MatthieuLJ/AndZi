package com.matthieu.zi_app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.data.preferences.UserPreferences
import com.matthieu.zi_app.domain.badges.BadgeCategory
import com.matthieu.zi_app.domain.badges.BadgeId
import com.matthieu.zi_app.ui.badges.BadgesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BadgesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var userPreferences: UserPreferences

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(UserPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        userPreferences = UserPreferences(context)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testBadgesViewModelLoadsSummary() = runBlocking {
        // Unlock one badge
        userPreferences.recordTestAnswer(isCorrect = true)
        val vm = BadgesViewModel(userPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = withTimeout(3000) {
            vm.uiState.filter { !it.isLoading }.first()
        }

        assertNotNull(state.summary)
        val summary = state.summary!!
        assertEquals(1, summary.currentCorrectStreak)
        assertEquals(1, summary.longestCorrectStreak)
        assertEquals(18, summary.badges.size)
        assertEquals(4, summary.badges.count { it.badge.category == BadgeCategory.DAILY_STREAK })
        assertEquals(4, summary.badges.count { it.badge.category == BadgeCategory.CORRECT_STREAK })
        assertEquals(4, summary.badges.count { it.badge.category == BadgeCategory.DAILY_TESTS })
        assertEquals(6, summary.badges.count { it.badge.category == BadgeCategory.HSK_LEVEL })
    }
}
