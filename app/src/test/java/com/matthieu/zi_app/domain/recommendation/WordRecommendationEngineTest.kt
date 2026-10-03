package com.matthieu.zi_app.domain.recommendation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.local.AndZiDatabase
import com.matthieu.zi_app.data.local.entity.HskVocabEntity
import com.matthieu.zi_app.data.repository.VocabularyRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class WordRecommendationEngineTest {

    private lateinit var dictDb: AndZiDatabase
    private lateinit var dictRepo: VocabularyRepository
    private lateinit var engine: WordRecommendationEngine

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dictDb = AndZiDatabase.createInMemory(context)
        dictRepo = VocabularyRepository(dictDb.vocabularyDao())
        engine = WordRecommendationEngine(dictRepo, temperature = 10.0)
    }

    @After
    fun teardown() {
        dictDb.close()
    }

    @Test
    fun testBeginnerRecommendationReturnsHsk1CurriculumWord() = runBlocking {
        // Seed HSK 1 entries
        dictRepo.insertHskVocab(
            listOf(
                HskVocabEntity("你", hskLevel = 1, freqRank = 1, isChar = 1),
                HskVocabEntity("好", hskLevel = 1, freqRank = 2, isChar = 1),
                HskVocabEntity("我", hskLevel = 1, freqRank = 3, isChar = 1)
            )
        )
        dictRepo.addVocabulary(Vocabulary("你", "ni3", arrayListOf("you")))
        dictRepo.addVocabulary(Vocabulary("好", "hao3", arrayListOf("good")))
        dictRepo.addVocabulary(Vocabulary("我", "wo3", arrayListOf("I", "me")))

        val recommendation = engine.recommendNextWord(
            knownCharacters = emptySet(),
            deckWords = emptySet(),
            random = Random(42)
        )

        assertNotNull(recommendation)
        assertTrue(recommendation.word in listOf("你", "好", "我"))
        assertTrue(recommendation.reason is SuggestionReason.HskCurriculum || recommendation.reason is SuggestionReason.HighFrequency)
    }

    @Test
    fun testComposedWordIsPrioritizedWhenConstituentCharactersAreKnown() = runBlocking {
        // Add single characters
        dictRepo.addVocabulary(Vocabulary("学", "xue2", arrayListOf("to study")))
        dictRepo.addVocabulary(Vocabulary("生", "sheng1", arrayListOf("to be born", "raw")))
        dictRepo.addVocabulary(Vocabulary("学生", "xue2sheng1", arrayListOf("student")))
        dictRepo.addVocabulary(Vocabulary("汽车", "qi4che1", arrayListOf("car")))

        dictRepo.insertHskVocab(
            listOf(
                HskVocabEntity("学", hskLevel = 1, freqRank = 100, isChar = 1),
                HskVocabEntity("生", hskLevel = 1, freqRank = 50, isChar = 1),
                HskVocabEntity("学生", hskLevel = 1, freqRank = 120, isChar = 0),
                HskVocabEntity("汽车", hskLevel = 2, freqRank = 500, isChar = 0)
            )
        )

        // User knows "学" and "生", but not "学生"
        val knownChars = setOf("学", "生")
        val deckWords = setOf("学", "生")

        val recommendation = engine.recommendNextWord(
            knownCharacters = knownChars,
            deckWords = deckWords,
            random = Random(42)
        )

        assertEquals("学生", recommendation.word)
        assertTrue(recommendation.reason is SuggestionReason.ComposedFromKnown)
        val composedReason = recommendation.reason as SuggestionReason.ComposedFromKnown
        assertTrue(composedReason.constituentChars.contains("学"))
        assertTrue(composedReason.constituentChars.contains("生"))
        assertEquals("Composed from known characters: 学, 生 • HSK 1", composedReason.formatDisplay())
    }

    @Test
    fun testExcludesWordsAlreadyInDeck() = runBlocking {
        dictRepo.addVocabulary(Vocabulary("你", "ni3", arrayListOf("you")))
        dictRepo.addVocabulary(Vocabulary("好", "hao3", arrayListOf("good")))
        dictRepo.addVocabulary(Vocabulary("大", "da4", arrayListOf("big")))

        dictRepo.insertHskVocab(
            listOf(
                HskVocabEntity("你", hskLevel = 1, freqRank = 1, isChar = 1),
                HskVocabEntity("好", hskLevel = 1, freqRank = 2, isChar = 1),
                HskVocabEntity("大", hskLevel = 1, freqRank = 10, isChar = 1)
            )
        )

        // "你" and "好" are in deckWords, so engine must choose "大"
        val recommendation = engine.recommendNextWord(
            knownCharacters = setOf("你", "好"),
            deckWords = setOf("你", "好"),
            random = Random(42)
        )

        assertEquals("大", recommendation.word)
        assertFalse(recommendation.word in setOf("你", "好"))
    }

    @Test
    fun testStochasticSamplingProvidesVarietyAcrossCalls() = runBlocking {
        val hskList = (1..20).map { i ->
            val w = "字$i"
            dictRepo.addVocabulary(Vocabulary(w, "zi$i", arrayListOf("word $i")))
            HskVocabEntity(w, hskLevel = 1, freqRank = i * 10, isChar = 1)
        }
        dictRepo.insertHskVocab(hskList)

        val results = mutableSetOf<String>()
        for (seed in 1..20) {
            val rec = engine.recommendNextWord(
                knownCharacters = emptySet(),
                deckWords = emptySet(),
                random = Random(seed)
            )
            results.add(rec.word)
        }

        // Multiple seeds should yield multiple different high-ranked recommendations
        assertTrue("Expected variety across 20 random seeds, but got ${results.size}", results.size > 1)
    }

    @Test
    fun testExcludesRecentlySuggestedWords() = runBlocking {
        dictRepo.addVocabulary(Vocabulary("你", "ni3", arrayListOf("you")))
        dictRepo.addVocabulary(Vocabulary("好", "hao3", arrayListOf("good")))
        dictRepo.addVocabulary(Vocabulary("他", "ta1", arrayListOf("he")))

        dictRepo.insertHskVocab(
            listOf(
                HskVocabEntity("你", hskLevel = 1, freqRank = 1, isChar = 1),
                HskVocabEntity("好", hskLevel = 1, freqRank = 2, isChar = 1),
                HskVocabEntity("他", hskLevel = 1, freqRank = 3, isChar = 1)
            )
        )

        // "你" and "好" are in excludedWords (e.g. recently suggested)
        val recommendation = engine.recommendNextWord(
            knownCharacters = emptySet(),
            deckWords = emptySet(),
            excludedWords = setOf("你", "好"),
            random = Random(42)
        )

        assertEquals("他", recommendation.word)
    }

    @Test
    fun testSuggestsNewHskCharactersWhenUserAlreadyKnowsCharacters() = runBlocking {
        // User knows "学" and "生"
        dictRepo.addVocabulary(Vocabulary("学", "xue2", arrayListOf("to study")))
        dictRepo.addVocabulary(Vocabulary("生", "sheng1", arrayListOf("to be born")))
        dictRepo.addVocabulary(Vocabulary("学生", "xue2sheng1", arrayListOf("student")))
        dictRepo.addVocabulary(Vocabulary("水", "shui3", arrayListOf("water")))

        dictRepo.insertHskVocab(
            listOf(
                HskVocabEntity("学", hskLevel = 1, freqRank = 100, isChar = 1),
                HskVocabEntity("生", hskLevel = 1, freqRank = 50, isChar = 1),
                HskVocabEntity("学生", hskLevel = 1, freqRank = 120, isChar = 0),
                HskVocabEntity("水", hskLevel = 1, freqRank = 10, isChar = 1) // High priority new character
            )
        )

        val knownChars = setOf("学", "生")
        val deckWords = setOf("学", "生")

        val results = mutableSetOf<String>()
        for (seed in 1..30) {
            val rec = engine.recommendNextWord(
                knownCharacters = knownChars,
                deckWords = deckWords,
                random = Random(seed)
            )
            results.add(rec.word)
        }

        // Must recommend both new foundational characters ("水") and composed words ("学生")
        assertTrue("Expected new character '水' to be recommended", results.contains("水"))
        assertTrue("Expected composed word '学生' to be recommended", results.contains("学生"))
    }

    @Test
    fun testSuggestsPartiallyComposedBridgeWord() = runBlocking {
        dictRepo.addVocabulary(Vocabulary("学", "xue2", arrayListOf("to study")))
        dictRepo.addVocabulary(Vocabulary("学院", "xue2yuan4", arrayListOf("institute, college")))

        dictRepo.insertHskVocab(
            listOf(
                HskVocabEntity("学", hskLevel = 1, freqRank = 100, isChar = 1),
                HskVocabEntity("学院", hskLevel = 2, freqRank = 300, isChar = 0)
            )
        )

        val recommendation = engine.recommendNextWord(
            knownCharacters = setOf("学"),
            deckWords = setOf("学"),
            random = Random(42)
        )

        assertEquals("学院", recommendation.word)
        assertTrue(recommendation.reason is SuggestionReason.PartiallyComposed)
        val partialReason = recommendation.reason as SuggestionReason.PartiallyComposed
        assertEquals("学", partialReason.knownChar)
        assertEquals("院", partialReason.newChar)
        assertEquals("Composed of known '学' + new '院' • HSK 2", partialReason.formatDisplay())
    }
}
