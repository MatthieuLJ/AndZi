package com.matthieu.zi_app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.local.AndZiDatabase
import com.matthieu.zi_app.data.local.PodcastDatabase
import com.matthieu.zi_app.data.local.entity.PodcastChannelEntity
import com.matthieu.zi_app.data.local.entity.PodcastEpisodeEntity
import com.matthieu.zi_app.data.local.entity.PodcastVocabEntity
import com.matthieu.zi_app.data.repository.PodcastRepository
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import com.matthieu.zi_app.domain.srs.ReviewGrade
import com.matthieu.zi_app.ui.podcast.player.PodcastAudioPlayer
import com.matthieu.zi_app.ui.podcast.player.PodcastPlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PodcastPlayerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var dictDb: AndZiDatabase
    private lateinit var userDb: AndZiDatabase
    private lateinit var podcastDb: PodcastDatabase
    private lateinit var dictRepo: VocabularyRepository
    private lateinit var userVocRepo: VocabularyRepository
    private lateinit var studyRepo: StudyRepository
    private lateinit var podcastRepo: PodcastRepository
    private lateinit var audioPlayer: PodcastAudioPlayer
    private lateinit var viewModel: PodcastPlayerViewModel

    private val testEpisodeId = 1

    @Before
    fun setup() {
        runBlocking {
            Dispatchers.setMain(testDispatcher)
            context = ApplicationProvider.getApplicationContext()
            context.deleteDatabase(PodcastDatabase.DATABASE_NAME)
            podcastDb = PodcastDatabase.getInstance(context)
            dictDb = AndZiDatabase.createInMemory(context)
            userDb = AndZiDatabase.createInMemory(context)

            dictRepo = VocabularyRepository(dictDb.vocabularyDao())
            userVocRepo = VocabularyRepository(userDb.vocabularyDao())
            studyRepo = StudyRepository(userDb.leitnerDao())
            podcastRepo = PodcastRepository(podcastDb.podcastDao(), context, dictDb.vocabularyDao())
            audioPlayer = PodcastAudioPlayer(context, CoroutineScope(testDispatcher))

            val channel = PodcastChannelEntity(
                channelId = 1,
                title = "Slow Chinese",
                chineseTitle = "慢速中文",
                feedUrl = "https://example.com/rss"
            )
            podcastDb.podcastDao().insertChannels(listOf(channel))

            val episode = PodcastEpisodeEntity(
                episodeId = testEpisodeId,
                channelId = 1,
                episodeNum = 1,
                title = "Episode 1",
                audioUrl = "https://example.com/ep1.mp3",
                transcript = "你好，世界！"
            )
            podcastDb.podcastDao().insertEpisodes(listOf(episode))

            // Seed dictionary with "你好" and "世界"
            dictRepo.addVocabulary(Vocabulary("你好", "ni3hao3", arrayListOf("hello", "how are you")))
            dictRepo.addVocabulary(Vocabulary("世界", "shi4jie4", arrayListOf("world")))

            viewModel = PodcastPlayerViewModel(
                episodeId = testEpisodeId,
                podcastRepository = podcastRepo,
                localVocabularyRepository = userVocRepo,
                dictionaryRepository = dictRepo,
                studyRepository = studyRepo,
                audioPlayer = audioPlayer
            )

            withTimeout(3000) {
                viewModel.uiState.filter { !it.isLoading }.first()
            }
        }
    }

    @After
    fun teardown() {
        PodcastDatabase.resetInstanceForTesting()
        Dispatchers.resetMain()
        dictDb.close()
        userDb.close()
    }

    @Test
    fun testWordNotInVocabularyShowsNotStudiedAndEmptySelectedTranslations() = runBlocking {
        viewModel.selectWordForDetail("你好")

        val state = withTimeout(3000) {
            viewModel.uiState.filter { it.selectedWord?.chinese == "你好" }.first()
        }
        assertEquals("你好", state.selectedWord?.chinese)
        assertFalse("Word should not be marked as in study", state.isInStudy)
        assertNull("Current box should be null for unstudied word", state.currentBox)
        assertTrue("Selected translations should be empty for unstudied word", state.selectedTranslations.isEmpty())
        assertEquals(2, state.selectedWord?.english?.size)
    }

    @Test
    fun testToggleTranslationAddsWordToStudyAndRemovesWhenEmpty() = runBlocking {
        viewModel.selectWordForDetail("你好")
        withTimeout(3000) {
            viewModel.uiState.filter { it.selectedWord?.chinese == "你好" }.first()
        }

        // 1. Select first translation "hello"
        viewModel.toggleTranslationForStudy("hello")
        var state = withTimeout(3000) {
            viewModel.uiState.filter { it.selectedTranslations.contains("hello") }.first()
        }
        assertTrue("Word should now be marked in study", state.isInStudy)
        assertEquals(0, state.currentBox)
        assertEquals(setOf("hello"), state.selectedTranslations)
        assertNotNull(studyRepo.getLeitnerState("你好"))
        val localVocabs = userVocRepo.getVocabularyForChinese("你好")
        assertEquals(1, localVocabs.size)
        assertEquals(listOf("hello"), localVocabs.first().english)

        // 2. Select second translation "how are you"
        viewModel.toggleTranslationForStudy("how are you")
        state = withTimeout(3000) {
            viewModel.uiState.filter { it.selectedTranslations.contains("how are you") }.first()
        }
        assertTrue(state.isInStudy)
        assertEquals(setOf("hello", "how are you"), state.selectedTranslations)

        // 3. Unselect first translation "hello"
        viewModel.toggleTranslationForStudy("hello")
        state = withTimeout(3000) {
            viewModel.uiState.filter { !it.selectedTranslations.contains("hello") }.first()
        }
        assertTrue(state.isInStudy)
        assertEquals(setOf("how are you"), state.selectedTranslations)

        // 4. Unselect the remaining translation "how are you" -> Word leaves study
        viewModel.toggleTranslationForStudy("how are you")
        state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isInStudy }.first()
        }
        assertFalse("Word should leave study when no translations remain", state.isInStudy)
        assertNull(state.currentBox)
        assertTrue(state.selectedTranslations.isEmpty())
        assertNull(studyRepo.getLeitnerState("你好"))
    }

    @Test
    fun testSelectWordAlreadyInVocabularyPreservesSelectedTranslationsAndBox() = runBlocking {
        // Pre-populate user vocabulary with only "hello" for "你好", Box 3
        userVocRepo.addVocabulary(Vocabulary("你好", "ni3hao3", arrayListOf("hello")))
        studyRepo.recordReviewGrade("你好", ReviewGrade.GOOD)
        studyRepo.recordReviewGrade("你好", ReviewGrade.GOOD)
        studyRepo.recordReviewGrade("你好", ReviewGrade.GOOD)
        val initialLeitner = studyRepo.getLeitnerState("你好")
        val expectedBox = initialLeitner?.box ?: 0
        assertTrue("Box should be > 0", expectedBox > 0)

        viewModel.selectWordForDetail("你好")

        val state = withTimeout(3000) {
            viewModel.uiState.filter { it.selectedWord?.chinese == "你好" && it.isInStudy }.first()
        }
        assertEquals(expectedBox, state.currentBox)
        assertEquals(setOf("hello"), state.selectedTranslations)
        // Both definitions should be in word.english for display so user can toggle them
        assertTrue(state.selectedWord!!.english.contains("hello"))
        assertTrue(state.selectedWord!!.english.contains("how are you"))
    }

    @Test
    fun testToggleStudyForWordAddsAllIfNoneSelectedAndRemovesIfStudied() = runBlocking {
        viewModel.selectWordForDetail("世界")
        withTimeout(3000) {
            viewModel.uiState.filter { it.selectedWord?.chinese == "世界" }.first()
        }
        assertFalse(viewModel.uiState.value.isInStudy)

        // Tap the main "Study" button
        viewModel.toggleStudyForWord(viewModel.uiState.value.selectedWord!!)
        var state = withTimeout(3000) {
            viewModel.uiState.filter { it.isInStudy && it.selectedWord?.chinese == "世界" }.first()
        }
        assertTrue(state.isInStudy)
        assertEquals(0, state.currentBox)
        assertEquals(setOf("world"), state.selectedTranslations)
        assertNotNull(studyRepo.getLeitnerState("世界"))

        // Tap the main "Study" button again to remove
        viewModel.toggleStudyForWord(viewModel.uiState.value.selectedWord!!)
        state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isInStudy && it.selectedWord?.chinese == "世界" }.first()
        }
        assertFalse(state.isInStudy)
        assertNull(state.currentBox)
        assertTrue(state.selectedTranslations.isEmpty())
        assertNull(studyRepo.getLeitnerState("世界"))
    }

    @Test
    fun testToggleStudyForVocabItemSyncsWithVocabList() = runBlocking {
        val initialList = viewModel.uiState.value.vocabItems
        assertTrue(initialList.any { it.vocab.chinese == "你好" })
        val vocabItem = initialList.first { it.vocab.chinese == "你好" }.vocab
        assertFalse(initialList.first { it.vocab.chinese == "你好" }.isStudied)

        // Toggle study for vocab item
        viewModel.toggleStudyForVocabItem(vocabItem)
        val updatedList = withTimeout(3000) {
            viewModel.uiState.filter { state ->
                state.vocabItems.find { it.vocab.chinese == "你好" }?.isStudied == true
            }.first()
        }.vocabItems
        assertTrue(updatedList.first { it.vocab.chinese == "你好" }.isStudied)
        assertNotNull(studyRepo.getLeitnerState("你好"))

        // Toggle off
        viewModel.toggleStudyForVocabItem(vocabItem)
        val revertedList = withTimeout(3000) {
            viewModel.uiState.filter { state ->
                state.vocabItems.find { it.vocab.chinese == "你好" }?.isStudied == false
            }.first()
        }.vocabItems
        assertFalse(revertedList.first { it.vocab.chinese == "你好" }.isStudied)
        assertNull(studyRepo.getLeitnerState("你好"))
    }
}
