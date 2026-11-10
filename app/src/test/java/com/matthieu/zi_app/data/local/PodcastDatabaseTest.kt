package com.matthieu.zi_app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.data.local.entity.ChineseWordEntity
import com.matthieu.zi_app.data.local.entity.EnglishTranslationEntity
import com.matthieu.zi_app.data.local.entity.PodcastEpisodeEntity
import com.matthieu.zi_app.data.repository.PodcastRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
class PodcastDatabaseTest {

    private lateinit var context: Context
    private lateinit var podcastDb: PodcastDatabase
    private lateinit var andZiDb: AndZiDatabase
    private lateinit var repository: PodcastRepository

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
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(PodcastDatabase.DATABASE_NAME)
        podcastDb = PodcastDatabase.getInstance(context)

        andZiDb = Room.inMemoryDatabaseBuilder(context, AndZiDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        repository = PodcastRepository(
            podcastDao = podcastDb.podcastDao(),
            context = context,
            dictionaryDao = andZiDb.vocabularyDao()
        )
    }

    @After
    fun tearDown() {
        PodcastDatabase.resetInstanceForTesting()
        andZiDb.close()
    }

    @Test
    fun testSeededChannels() = runBlocking {
        val dao = podcastDb.podcastDao()
        val channels = dao.getAllChannels().first()

        assertEquals("Expected 4 seeded channels", 4, channels.size)
        assertEquals("Slow Chinese", channels[0].title)
        assertEquals("慢速中文", channels[0].chineseTitle)
        assertTrue(channels[0].supportsPacedHighlighting)
        assertEquals("Reading China", channels[1].title)
        assertEquals("中文天天读", channels[1].chineseTitle)
        assertTrue(channels[1].supportsPacedHighlighting)
        assertEquals("MaoMi Chinese", channels[2].title)
        assertEquals(12000, channels[2].introOffsetMs)
        assertEquals(16000, channels[2].outroOffsetMs)
        assertTrue(channels[2].isEnabled)
        org.junit.Assert.assertFalse(channels[2].supportsPacedHighlighting)
        assertEquals("TeaTime Chinese", channels[3].title)
        assertTrue(channels[3].isEnabled)
        assertTrue(channels[3].supportsPacedHighlighting)
    }

    @Test
    fun testSyncFromInputStreamAndCounts() = runBlocking {
        val stream = ByteArrayInputStream(sampleRssXml.toByteArray(Charsets.UTF_8))
        val result = repository.syncFromInputStream(1, stream)

        assertTrue("Sync should succeed", result.isSuccess)
        assertEquals(3, result.getOrNull())

        val slowChineseCount = repository.getEpisodeCountForChannel(1)
        val readingChinaCount = repository.getEpisodeCountForChannel(2)

        assertEquals("Expected 2 Slow Chinese episodes", 2, slowChineseCount)
        assertEquals("Expected 1 Reading China episode", 1, readingChinaCount)

        val ep1 = podcastDb.podcastDao().getEpisodeById(1)
        assertNotNull(ep1)
        assertTrue(ep1!!.title.contains("端午节"))
        assertEquals("端午节是中国传统节日。人们吃粽子、划龙舟。", ep1.transcript)
        assertEquals(210000, ep1.durationMs) // 03:30 = 210,000 ms
    }

    @Test
    fun testSyncPreservesPlaybackAndDownload() = runBlocking {
        // First sync
        repository.syncFromInputStream(1, ByteArrayInputStream(sampleRssXml.toByteArray(Charsets.UTF_8)))
        val ep1 = podcastDb.podcastDao().getEpisodeById(1)!!

        // Simulate user playback and download
        podcastDb.podcastDao().updatePlaybackPosition(ep1.episodeId, 45000, true)
        podcastDb.podcastDao().updateDownloadedPath(ep1.episodeId, "/path/to/downloaded.mp3")

        // Re-sync
        repository.syncFromInputStream(1, ByteArrayInputStream(sampleRssXml.toByteArray(Charsets.UTF_8)))

        val updatedEp1 = podcastDb.podcastDao().getEpisodeById(1)!!
        assertEquals(45000, updatedEp1.playbackPositionMs)
        assertTrue(updatedEp1.isCompleted)
        assertEquals("/path/to/downloaded.mp3", updatedEp1.downloadedPath)
    }

    @Test
    fun testDynamicVocabExtraction() = runBlocking {
        // 1. Seed some dictionary entries in andZiDb
        val vocDao = andZiDb.vocabularyDao()
        val cword1Id = vocDao.insertWord(ChineseWordEntity(chinese = "中国", pinyin = "zhong1 guo2", length = 2))
        vocDao.insertTranslation(EnglishTranslationEntity(cwordId = cword1Id, translation = "China"))

        val cword2Id = vocDao.insertWord(ChineseWordEntity(chinese = "节日", pinyin = "jie2 ri4", length = 2))
        vocDao.insertTranslation(EnglishTranslationEntity(cwordId = cword2Id, translation = "holiday; festival"))

        val cword3Id = vocDao.insertWord(ChineseWordEntity(chinese = "粽子", pinyin = "zong4 zi5", length = 2))
        vocDao.insertTranslation(EnglishTranslationEntity(cwordId = cword3Id, translation = "sticky rice dumpling"))

        // 2. Insert episode
        val epId = podcastDb.podcastDao().insertEpisodes(
            listOf(
                PodcastEpisodeEntity(
                    channelId = 1,
                    episodeNum = 1,
                    title = "Test",
                    audioUrl = "http://example.com/audio.mp3",
                    transcript = "端午节是中国传统节日。人们吃粽子、划龙舟。"
                )
            )
        ).first().toInt()

        // 3. Extract vocabulary
        val vocab = repository.extractAndSaveVocabForEpisode(epId, "端午节是中国传统节日。人们吃粽子、划龙舟。")

        assertEquals(3, vocab.size)
        assertEquals("中国", vocab[0].chinese)
        assertEquals("zhong1 guo2", vocab[0].pinyin)
        assertEquals("China", vocab[0].translation)

        assertEquals("节日", vocab[1].chinese)
        assertEquals("粽子", vocab[2].chinese)

        // 4. Verify cached in DB
        val fromDb = podcastDb.podcastDao().getVocabForEpisodeSync(epId)
        assertEquals(3, fromDb.size)
    }

    @Test
    fun testTargetHskLevelAndMasteryFlow() = runBlocking {
        andZiDb.vocabularyDao().insertHskVocab(
            listOf(
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "中国", hskLevel = 1, freqRank = 10),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "节日", hskLevel = 2, freqRank = 20),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "粽子", hskLevel = 3, freqRank = 30)
            )
        )
        andZiDb.vocabularyDao().insertWords(
            listOf(
                ChineseWordEntity(chinese = "中国", pinyin = "zhong1 guo2", length = 2),
                ChineseWordEntity(chinese = "节日", pinyin = "jie2 ri4", length = 2),
                ChineseWordEntity(chinese = "粽子", pinyin = "zong4 zi5", length = 2)
            )
        )

        val epId = podcastDb.podcastDao().insertEpisodes(
            listOf(
                PodcastEpisodeEntity(
                    channelId = 1,
                    episodeNum = 1,
                    title = "Test Episode",
                    audioUrl = "http://example.com/audio.mp3",
                    transcript = "中国节日人们吃粽子。"
                )
            )
        ).first().toInt()

        repository.extractAndSaveVocabForEpisode(epId, "中国节日人们吃粽子。")

        val ep = podcastDb.podcastDao().getEpisodeById(epId)
        assertNotNull(ep)
        assertTrue("Target HSK level should be graded (> 0)", ep!!.targetHskLevel > 0)

        val allWords = repository.getAllVocabWordsSync()
        assertEquals(3, allWords.size)
        assertEquals(epId, allWords[0].episodeId)
    }

    @Test
    fun testSeedMaoMiEpisodesFromAssets() = runBlocking {
        val seedResult = repository.seedMaoMiEpisodesFromAssets()
        assertTrue("MaoMi seed should succeed", seedResult.isSuccess)
        assertEquals(271, seedResult.getOrNull())

        val maomiCount = repository.getEpisodeCountForChannel(3)
        assertEquals(271, maomiCount)

        val ep1 = podcastDb.podcastDao().getEpisodesForChannelSync(3).firstOrNull { it.episodeNum == 1 }
        assertNotNull(ep1)
        assertTrue(ep1!!.title.contains("Louie"))
        assertTrue(ep1.transcript.isNotBlank())
        assertTrue("Episode 1 should contain verbatim transcript with Guangdong intro", ep1.transcript.contains("广东省"))
        assertEquals("Episode 1 should have targetHskLevel == 3", 3, ep1.targetHskLevel)

        val vocabCount = repository.getVocabCountForChannel(3)
        assertTrue("MaoMi vocabulary should be seeded (>= 30000 items, got $vocabCount)", vocabCount >= 30000)

        val ep1Vocab = podcastDb.podcastDao().getVocabForEpisodeSync(ep1.episodeId)
        assertTrue("Episode 1 should have vocabulary items", ep1Vocab.size >= 100)

        val ep5 = podcastDb.podcastDao().getEpisodesForChannelSync(3).firstOrNull { it.episodeNum == 5 }
        assertNotNull(ep5)
        val ep5Vocab = podcastDb.podcastDao().getVocabForEpisodeSync(ep5!!.episodeId)
        assertTrue("Episode 5 should have precomputed vocabulary (got ${ep5Vocab.size})", ep5Vocab.size >= 50)

        val ep134 = podcastDb.podcastDao().getEpisodesForChannelSync(3).firstOrNull { it.episodeNum == 134 }
        assertNotNull(ep134)
        assertTrue(ep134!!.transcript.contains("脸"))
        assertTrue("Episode 134 should have targetHskLevel > 0", ep134.targetHskLevel > 0)
        val ep134Vocab = podcastDb.podcastDao().getVocabForEpisodeSync(ep134.episodeId)
        assertTrue("Episode 134 should have precomputed vocabulary (got ${ep134Vocab.size})", ep134Vocab.size >= 50)

        // Verify that extracting sentences for MaoMi returns empty list because supportsPacedHighlighting is false
        val channel3 = podcastDb.podcastDao().getChannelById(3)
        val sentences = repository.extractAndSaveSentencesForEpisode(ep1, channel3)
        assertTrue("MaoMi Chinese should have no synthetic sentence timestamps", sentences.isEmpty())
    }

    @Test
    fun testTeaTimeSeedFromAssets() = runBlocking {
        val seedResult = repository.seedTeaTimeEpisodesFromAssets()
        assertTrue("TeaTime seeding should succeed: ${seedResult.exceptionOrNull()?.message}", seedResult.isSuccess)
        assertEquals(100, seedResult.getOrNull())

        val teaTimeCount = repository.getEpisodeCountForChannel(4)
        assertEquals(100, teaTimeCount)

        val ep1 = podcastDb.podcastDao().getEpisodesForChannelSync(4).firstOrNull { it.episodeNum == 1 }
        assertNotNull(ep1)
        assertTrue(ep1!!.title.contains("茶歇中文"))
        assertTrue(ep1.transcript.isNotBlank())
        assertEquals(3, ep1.targetHskLevel)

        val sentenceCount = repository.getSentenceCountForChannel(4)
        assertTrue("TeaTime sentences should be seeded (>= 15000 items, got $sentenceCount)", sentenceCount >= 15000)

        val ep1Sentences = podcastDb.podcastDao().getSentencesForEpisodeSync(ep1.episodeId)
        assertTrue("Episode 1 should have sentences (got ${ep1Sentences.size})", ep1Sentences.size >= 80)
        assertTrue("Episode 1 sentence 2 should have endMs > 0", ep1Sentences[1].endMs > 0)

        val vocabCount = repository.getVocabCountForChannel(4)
        assertTrue("TeaTime vocabulary should be seeded (>= 20000 items, got $vocabCount)", vocabCount >= 20000)

        val ep1Vocab = podcastDb.podcastDao().getVocabForEpisodeSync(ep1.episodeId)
        assertTrue("Episode 1 should have vocabulary items (got ${ep1Vocab.size})", ep1Vocab.size >= 100)

        // Verify that extractAndSaveSentencesForEpisode returns existing SRT sentences without overwriting
        val channel4 = podcastDb.podcastDao().getChannelById(4)
        val extractedSentences = repository.extractAndSaveSentencesForEpisode(ep1, channel4)
        assertEquals(ep1Sentences.size, extractedSentences.size)
        assertEquals(ep1Sentences[0].chineseText, extractedSentences[0].chineseText)
    }
}
