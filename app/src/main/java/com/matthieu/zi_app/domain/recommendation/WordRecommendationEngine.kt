package com.matthieu.zi_app.domain.recommendation

import com.matthieu.zi_app.data.local.entity.HskVocabEntity
import com.matthieu.zi_app.data.repository.VocabularyRepository
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.random.Random

class WordRecommendationEngine(
    private val dictionaryRepository: VocabularyRepository,
    private val temperature: Double = 12.0
) {

    suspend fun recommendNextWord(
        knownCharacters: Set<String>,
        deckWords: Set<String>,
        random: Random = Random.Default
    ): WordRecommendation {
        // 1. Determine user's current HSK target level
        val targetHskLevel = estimateTargetHskLevel(deckWords)

        // 2. Fetch candidate words from dictionary repository
        val candidateMap = mutableMapOf<String, ScoredCandidate>()

        // 2a. 100% Composed compound words (if user knows characters)
        if (knownCharacters.isNotEmpty()) {
            val composedWordEntities = dictionaryRepository.getComposedWords(
                knownChars = knownCharacters.toList(),
                limit = 200
            )
            val unstudiedComposed = composedWordEntities
                .map { it.chinese }
                .filter { it !in deckWords }
                .distinct()

            if (unstudiedComposed.isNotEmpty()) {
                val hskMetadataMap = dictionaryRepository.getHskVocabForWords(unstudiedComposed)
                    .associateBy { it.hanzi }

                for (word in unstudiedComposed) {
                    val hskEntity = hskMetadataMap[word]
                    val constituentChars = getConstituentCharacters(word)
                    val score = computeCandidateScore(
                        word = word,
                        hskEntity = hskEntity,
                        knownChars = knownCharacters,
                        targetHskLevel = targetHskLevel,
                        isComposed = true
                    )
                    val reason = SuggestionReason.ComposedFromKnown(
                        constituentChars = constituentChars,
                        hskLevel = hskEntity?.hskLevel
                    )
                    candidateMap[word] = ScoredCandidate(word, score, reason)
                }
            }
        }

        // 2b. Curated HSK & frequency candidates
        val maxLevelToFetch = if (targetHskLevel <= 1) 2 else minOf(9, targetHskLevel + 1)
        val hskCandidates = dictionaryRepository.getHskCandidates(
            maxLevel = maxLevelToFetch,
            limit = 300
        ).filter { it.hanzi !in deckWords }

        for (hskEntity in hskCandidates) {
            val word = hskEntity.hanzi
            if (word in candidateMap) continue // Already evaluated with full composition

            val constituentChars = getConstituentCharacters(word)
            val knownCount = constituentChars.count { it in knownCharacters }
            val isPartial = word.length == 2 && knownCount == 1

            val score = computeCandidateScore(
                word = word,
                hskEntity = hskEntity,
                knownChars = knownCharacters,
                targetHskLevel = targetHskLevel,
                isComposed = false
            )

            val reason = when {
                isPartial -> {
                    val knownChar = constituentChars.first { it in knownCharacters }
                    val newChar = constituentChars.first { it !in knownCharacters }
                    SuggestionReason.PartiallyComposed(knownChar, newChar, hskEntity.hskLevel)
                }
                hskEntity.hskLevel in 1..9 -> {
                    SuggestionReason.HskCurriculum(hskEntity.hskLevel, isChar = hskEntity.isChar == 1)
                }
                else -> {
                    SuggestionReason.HighFrequency(hskEntity.freqRank)
                }
            }
            candidateMap[word] = ScoredCandidate(word, score, reason)
        }

        // 3. Fallback if candidate pool is empty
        if (candidateMap.isEmpty()) {
            return WordRecommendation(
                word = "你",
                reason = SuggestionReason.HskCurriculum(level = 1, isChar = true)
            )
        }

        // 4. Select top candidates and apply Softmax temperature sampling
        val topCandidates = candidateMap.values
            .sortedByDescending { it.score }
            .take(20)

        val selected = sampleWithSoftmax(topCandidates, temperature, random)
        return WordRecommendation(
            word = selected.word,
            reason = selected.reason
        )
    }

    private suspend fun estimateTargetHskLevel(deckWords: Set<String>): Int {
        if (deckWords.isEmpty()) return 1
        val hskEntries = dictionaryRepository.getHskVocabForWords(deckWords.take(50).toList())
        if (hskEntries.isEmpty()) return 1

        val validLevels = hskEntries.map { it.hskLevel }.filter { it in 1..9 }
        if (validLevels.isEmpty()) return 1

        val sortedLevels = validLevels.sorted()
        return sortedLevels[sortedLevels.size / 2] // Median HSK level
    }

    private fun computeCandidateScore(
        word: String,
        hskEntity: HskVocabEntity?,
        knownChars: Set<String>,
        targetHskLevel: Int,
        isComposed: Boolean
    ): Double {
        val charLength = word.codePointCount(0, word.length)
        val constituentChars = getConstituentCharacters(word)
        val knownCount = constituentChars.count { it in knownChars }

        // 1. Composition Score
        val compScore = when {
            isComposed || (charLength > 1 && knownCount == charLength) -> 100.0
            charLength == 2 && knownCount == 1 -> 35.0
            charLength == 1 && knownCount == 0 -> 20.0
            else -> 0.0
        }

        // 2. HSK Level Score
        val hskLevel = hskEntity?.hskLevel ?: 99
        val levelDiff = abs(hskLevel - targetHskLevel)
        val hskScore = if (hskLevel in 1..9) {
            max(0.0, 50.0 - (levelDiff * 12.0) - (max(0, hskLevel - targetHskLevel) * 8.0))
        } else {
            0.0
        }

        // 3. Frequency Score
        val freqRank = hskEntity?.freqRank ?: 999999
        val freqScore = max(0.0, 30.0 - 3.5 * ln(max(1.0, freqRank.toDouble())))

        // 4. Length Score
        val lenScore = when (charLength) {
            2 -> 10.0
            1 -> 8.0
            3 -> 4.0
            else -> 0.0
        }

        return compScore + hskScore + freqScore + lenScore
    }

    private fun sampleWithSoftmax(
        candidates: List<ScoredCandidate>,
        temp: Double,
        random: Random
    ): ScoredCandidate {
        if (candidates.size == 1) return candidates.first()

        val maxScore = candidates.maxOf { it.score }
        val weights = candidates.map { exp((it.score - maxScore) / max(0.1, temp)) }
        val totalWeight = weights.sum()

        val randomThreshold = random.nextDouble() * totalWeight
        var cumulative = 0.0
        for (i in candidates.indices) {
            cumulative += weights[i]
            if (cumulative >= randomThreshold) {
                return candidates[i]
            }
        }
        return candidates.last()
    }

    private fun getConstituentCharacters(chinese: String): List<String> {
        val list = mutableListOf<String>()
        var i = 0
        while (i < chinese.length) {
            val codePoint = chinese.codePointAt(i)
            list.add(String(Character.toChars(codePoint)))
            i += Character.charCount(codePoint)
        }
        return list
    }

    private data class ScoredCandidate(
        val word: String,
        val score: Double,
        val reason: SuggestionReason
    )
}
