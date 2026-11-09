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
        assertEquals("Reading China", channels[1].title)
        assertEquals("中文天天读", channels[1].chineseTitle)
        assertEquals("MaoMi Chinese", channels[2].title)
        assertEquals(12000, channels[2].introOffsetMs)
        assertEquals(16000, channels[2].outroOffsetMs)
        assertTrue(channels[2].isEnabled)
        assertEquals("TeaTime Chinese", channels[3].title)
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

        val ep134 = podcastDb.podcastDao().getEpisodesForChannelSync(3).firstOrNull { it.episodeNum == 134 }
        assertNotNull(ep134)
        assertTrue(ep134!!.transcript.contains("脸"))
    }
}
