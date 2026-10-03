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
    private val temperature: Double = 18.0
) {

    suspend fun recommendNextWord(
        knownCharacters: Set<String>,
        deckWords: Set<String>,
        excludedWords: Set<String> = emptySet(),
        random: Random = Random.Default
    ): WordRecommendation {
        // 1. Determine user's current HSK target level
        val targetHskLevel = estimateTargetHskLevel(deckWords)

        // 2. Fetch candidate words from dictionary repository
        val candidateMap = mutableMapOf<String, ScoredCandidate>()

        // 2a. 100% Composed compound words from known characters
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
                    val score = computeComposedScore(hskEntity, targetHskLevel, word.length)
                    val reason = SuggestionReason.ComposedFromKnown(
                        constituentChars = constituentChars,
                        hskLevel = hskEntity?.hskLevel
                    )
                    candidateMap[word] = ScoredCandidate(word, score, reason)
                }
            }
        }

        // 2b. Curated HSK & frequency candidates (fetch up to target level + 1, min 2)
        val maxLevelToFetch = if (targetHskLevel <= 1) 2 else minOf(9, targetHskLevel + 1)
        val hskCandidates = dictionaryRepository.getHskCandidates(
            maxLevel = maxLevelToFetch,
            limit = 500
        ).filter { it.hanzi !in deckWords }

        for (hskEntity in hskCandidates) {
            val word = hskEntity.hanzi
            val constituentChars = getConstituentCharacters(word)
            val charLength = constituentChars.size
            val knownCount = constituentChars.count { it in knownCharacters }
            val isPartial = charLength == 2 && knownCount == 1
            val isComposed = charLength > 1 && knownCount == charLength

            val (score, reason) = when {
                isComposed -> {
                    val sc = computeComposedScore(hskEntity, targetHskLevel, charLength)
                    val r = SuggestionReason.ComposedFromKnown(constituentChars, hskEntity.hskLevel)
                    sc to r
                }
                isPartial -> {
                    val knownChar = constituentChars.first { it in knownCharacters }
                    val newChar = constituentChars.first { it !in knownCharacters }
                    val sc = computePartialScore(hskEntity, targetHskLevel)
                    val r = SuggestionReason.PartiallyComposed(knownChar, newChar, hskEntity.hskLevel)
                    sc to r
                }
                charLength == 1 && knownCount == 0 -> {
                    val sc = computeNewCharScore(hskEntity, targetHskLevel)
                    val r = if (hskEntity.hskLevel in 1..9) {
                        SuggestionReason.HskCurriculum(hskEntity.hskLevel, isChar = true)
                    } else {
                        SuggestionReason.HighFrequency(hskEntity.freqRank)
                    }
                    sc to r
                }
                else -> {
                    val sc = computeNewWordScore(hskEntity, targetHskLevel, charLength)
                    val r = if (hskEntity.hskLevel in 1..9) {
                        SuggestionReason.HskCurriculum(hskEntity.hskLevel, isChar = false)
                    } else {
                        SuggestionReason.HighFrequency(hskEntity.freqRank)
                    }
                    sc to r
                }
            }

            // If word was already in candidateMap, only replace if score is higher
            val existing = candidateMap[word]
            if (existing == null || score > existing.score) {
                candidateMap[word] = ScoredCandidate(word, score, reason)
            }
        }

        // 3. Fallback if candidate pool is empty
        if (candidateMap.isEmpty()) {
            return WordRecommendation(
                word = "你",
                reason = SuggestionReason.HskCurriculum(level = 1, isChar = true)
            )
        }

        // 4. Filter by excludedWords if possible
        val availableCandidates = if (excludedWords.isNotEmpty()) {
            val nonExcluded = candidateMap.values.filter { it.word !in excludedWords }
            if (nonExcluded.isNotEmpty()) nonExcluded else candidateMap.values.toList()
        } else {
            candidateMap.values.toList()
        }

        // 5. Select top candidates and apply Softmax temperature sampling
        val topCandidates = availableCandidates
            .sortedByDescending { it.score }
            .take(50)

        val selected = sampleWithSoftmax(topCandidates, temperature, random)
        return WordRecommendation(
            word = selected.word,
            reason = selected.reason
        )
    }

    private suspend fun estimateTargetHskLevel(deckWords: Set<String>): Int {
        if (deckWords.isEmpty()) return 1
        val sample = deckWords.take(100).toList()
        val hskEntries = dictionaryRepository.getHskVocabForWords(sample)
        if (hskEntries.isEmpty()) return 1

        val validLevels = hskEntries.map { it.hskLevel }.filter { it in 1..9 }
        if (validLevels.isEmpty()) return 1

        val sortedLevels = validLevels.sorted()
        return sortedLevels[sortedLevels.size / 2] // Median HSK level
    }

    private fun computeNewCharScore(hskEntity: HskVocabEntity, targetHskLevel: Int): Double {
        val hskScore = computeHskScore(hskEntity.hskLevel, targetHskLevel)
        val freqScore = computeFreqScore(hskEntity.freqRank)
        return 20.0 + hskScore + freqScore
    }

    private fun computePartialScore(hskEntity: HskVocabEntity, targetHskLevel: Int): Double {
        val hskScore = computeHskScore(hskEntity.hskLevel, targetHskLevel)
        val freqScore = computeFreqScore(hskEntity.freqRank)
        return 20.0 + hskScore + freqScore
    }

    private fun computeNewWordScore(hskEntity: HskVocabEntity, targetHskLevel: Int, length: Int): Double {
        val hskScore = computeHskScore(hskEntity.hskLevel, targetHskLevel)
        val freqScore = computeFreqScore(hskEntity.freqRank)
        val lenBonus = if (length == 2) 5.0 else 0.0
        return 15.0 + hskScore + freqScore + lenBonus
    }

    private fun computeComposedScore(hskEntity: HskVocabEntity?, targetHskLevel: Int, length: Int): Double {
        val hskLevel = hskEntity?.hskLevel ?: 99
        val freqRank = hskEntity?.freqRank ?: 999999
        val hskScore = computeHskScore(hskLevel, targetHskLevel)
        val freqScore = computeFreqScore(freqRank)
        val lenBonus = if (length == 2) 5.0 else 0.0
        return 20.0 + hskScore + freqScore + lenBonus
    }

    private fun computeHskScore(hskLevel: Int, targetHskLevel: Int): Double {
        if (hskLevel !in 1..9) return 0.0
        val levelDiff = abs(hskLevel - targetHskLevel)
        val higherPenalty = max(0, hskLevel - targetHskLevel) * 8.0
        return max(0.0, 50.0 - (levelDiff * 12.0) - higherPenalty)
    }

    private fun computeFreqScore(freqRank: Int): Double {
        return max(0.0, 35.0 - 3.5 * ln(max(1.0, freqRank.toDouble())))
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
