package com.matthieu.zi_app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.data.local.PodcastDatabase
import com.matthieu.zi_app.data.repository.PodcastRepository
import com.matthieu.zi_app.ui.podcast.catalog.PodcastCatalogViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PodcastCatalogViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: PodcastDatabase
    private lateinit var repository: PodcastRepository
    private lateinit var viewModel: PodcastCatalogViewModel

    private val sampleRssXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
          <channel>
            <title>Slow Chinese</title>
            <item>
              <title>Slow Chinese #001: 端午节 (Dragon Boat Festival)</title>
              <enclosure url="https://archive.org/download/slowchinese/001.mp3" type="audio/mpeg" length="12345"/>
              <duration>03:30</duration>
              <pubDate>Mon, 01 Jan 2024 12:00:00 GMT</pubDate>
              <description>&lt;p&gt;端午节是中国传统节日。人们吃粽子、划龙舟。&lt;/p&gt;</description>
            </item>
            <item>
              <title>Slow Chinese #002: 中秋节 (Mid-Autumn Festival)</title>
              <enclosure url="https://archive.org/download/slowchinese/002.mp3" type="audio/mpeg" length="12345"/>
              <duration>04:00</duration>
              <pubDate>Tue, 02 Jan 2024 12:00:00 GMT</pubDate>
              <description>&lt;p&gt;中秋节是赏月吃月饼的日子。&lt;/p&gt;</description>
            </item>
            <item>
              <title>中文天天读 #001: 北京烤鸭 (Peking Duck)</title>
              <enclosure url="https://archive.org/download/slowchinese/rd001.mp3" type="audio/mpeg" length="12345"/>
              <duration>02:15</duration>
              <pubDate>Wed, 03 Jan 2024 12:00:00 GMT</pubDate>
              <description>&lt;p&gt;北京烤鸭很有名。&lt;/p&gt;</description>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(PodcastDatabase.DATABASE_NAME)
        database = PodcastDatabase.getInstance(context)
        repository = PodcastRepository(database.podcastDao(), context)

        // Seed 3 test episodes into the database via repository
        runBlocking {
            repository.syncFromInputStream(1, ByteArrayInputStream(sampleRssXml.toByteArray(Charsets.UTF_8)))
        }

        viewModel = PodcastCatalogViewModel(repository, sharingStarted = kotlinx.coroutines.flow.SharingStarted.Eagerly)
    }

    @After
    fun tearDown() {
        PodcastDatabase.resetInstanceForTesting()
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialCatalogState() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.first { it.channels.isNotEmpty() && it.episodes.isNotEmpty() }
        assertEquals(4, state.channels.size)
        assertEquals(3, state.episodes.size)
        assertFalse(state.isLoading)
    }

    @Test
    fun testFilterByChannel() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        // Filter by channel 1 (Slow Chinese)
        viewModel.selectChannel(1)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.first { it.selectedChannelId == 1 }
        assertEquals(2, state.episodes.size)
        assertTrue(state.episodes.all { it.episode.channelId == 1 })

        // Filter by channel 2 (Reading China)
        viewModel.selectChannel(2)
        testDispatcher.scheduler.advanceUntilIdle()

        val state2 = viewModel.uiState.first { it.selectedChannelId == 2 }
        assertEquals(1, state2.episodes.size)
        assertTrue(state2.episodes.all { it.episode.channelId == 2 })
    }

    @Test
    fun testSearchQueryFilter() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setSearchQuery("端午节")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.first { it.searchQuery == "端午节" }
        assertEquals(1, state.episodes.size)
        assertTrue(state.episodes.first().episode.title.contains("端午节"))
    }

    @Test
    fun testHskLevelFilter() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        val allEpisodes = database.podcastDao().getAllEpisodesSync()
        assertTrue(allEpisodes.size >= 2)
        val ep1 = allEpisodes[0]
        val ep2 = allEpisodes[1]

        database.podcastDao().updateTargetHskLevel(ep1.episodeId, 2)
        database.podcastDao().updateTargetHskLevel(ep2.episodeId, 3)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectHskLevel(2)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.first { it.selectedHskLevel == 2 && it.episodes.isNotEmpty() }
        assertEquals(1, state.episodes.size)
        assertEquals(2, state.episodes.first().targetHskLevel)

        viewModel.selectHskLevel(null)
        testDispatcher.scheduler.advanceUntilIdle()
        val allState = viewModel.uiState.first { it.selectedHskLevel == null }
        assertEquals(3, allState.episodes.size)
    }
}
