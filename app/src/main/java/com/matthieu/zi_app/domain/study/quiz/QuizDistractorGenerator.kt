package com.matthieu.zi_app.domain.study.quiz

import com.matthieu.zi_app.PinyinParser
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import com.matthieu.zi_app.ui.study.QuizType

object QuizDistractorGenerator {

    val DEFAULT_FALLBACK_ENGLISH_DISTRACTORS = listOf(
        "to see; to look",
        "water",
        "person; people",
        "big; large",
        "small; tiny",
        "to eat",
        "to go",
        "good; well",
        "friend",
        "book; letter",
        "sun; day",
        "moon; month",
        "mountain",
        "heart; mind",
        "tree; wood",
        "fire",
        "to speak; to say",
        "time; hour",
        "sky; heaven",
        "home; family"
    )

    val DEFAULT_FALLBACK_PINYIN_DISTRACTORS = listOf(
        "hao3", "ni3", "da4", "xiao3", "shui3", "ren2", "tian1", "di4",
        "zhong1", "guo2", "shang4", "xia4", "yue4", "ri4", "shan1", "mu4"
    )

    fun getDistractorCountForBox(box: Int, quizType: QuizType): Int {
        return when (quizType) {
            QuizType.CLOZE -> when {
                box <= 0 -> 2
                box <= 2 -> 3
                box <= 4 -> 4
                else -> 5
            }
            QuizType.WORD_ASSEMBLY -> when {
                box <= 0 -> 2
                box <= 2 -> 3
                box <= 4 -> 4
                else -> 5
            }
            else -> 3
        }
    }

    fun countCharacterOccurrences(text: String, char: String): Int {
        if (char.isEmpty() || text.isEmpty()) return 0
        var count = 0
        var idx = 0
        while (idx <= text.length - char.length) {
            val found = text.indexOf(char, idx)
            if (found >= 0) {
                count++
                idx = found + char.length
            } else {
                break
            }
        }
        return count
    }

    fun splitTwoCharWord(word: String): Pair<String, String>? {
        val count = word.codePointCount(0, word.length)
        if (count != 2) return null
        val cp0 = word.codePointAt(0)
        val char0 = String(Character.toChars(cp0))
        val cp1 = word.codePointAt(char0.length)
        val char1 = String(Character.toChars(cp1))
        return Pair(char0, char1)
    }

    suspend fun isWordInDictionary(
        word: String,
        vocabularyRepository: VocabularyRepository,
        dictionaryRepository: VocabularyRepository?
    ): Boolean {
        if (dictionaryRepository != null) {
            return dictionaryRepository.getVocabularyForChinese(word).isNotEmpty()
        }
        return vocabularyRepository.getVocabularyForChinese(word).isNotEmpty()
    }

    suspend fun findMatchCompanions(
        primaryWord: String,
        targetPairCount: Int = 3,
        minPairCount: Int = 3,
        candidates: List<Vocabulary>,
        vocabularyRepository: VocabularyRepository,
        dictionaryRepository: VocabularyRepository?
    ): List<Vocabulary> {
        val primarySplit = splitTwoCharWord(primaryWord) ?: return emptyList()
        val chosen = mutableListOf<Vocabulary>()
        val chosenFirstChars = mutableSetOf(primarySplit.first)
        val chosenSecondChars = mutableSetOf(primarySplit.second)
        val allChosenChars = mutableSetOf(primarySplit.first, primarySplit.second)

        for (cand in candidates) {
            if (chosen.size >= targetPairCount - 1) break
            val candSplit = splitTwoCharWord(cand.chinese) ?: continue
            // Bijection guarantee: no duplicate 1st or 2nd character, and no duplicate across columns
            if (candSplit.first in allChosenChars || candSplit.second in allChosenChars) continue

            // CC-CEDICT anti-collision check: ensure cross-pairs do not form valid words
            var hasCrossCollision = false
            for (prevFirst in chosenFirstChars) {
                if (isWordInDictionary(prevFirst + candSplit.second, vocabularyRepository, dictionaryRepository)) {
                    hasCrossCollision = true
                    break
                }
            }
            if (hasCrossCollision) continue

            for (prevSecond in chosenSecondChars) {
                if (isWordInDictionary(candSplit.first + prevSecond, vocabularyRepository, dictionaryRepository)) {
                    hasCrossCollision = true
                    break
                }
            }
            if (hasCrossCollision) continue

            chosen.add(cand)
            chosenFirstChars.add(candSplit.first)
            chosenSecondChars.add(candSplit.second)
            allChosenChars.add(candSplit.first)
            allChosenChars.add(candSplit.second)
        }

        return if (chosen.size >= minPairCount - 1) chosen else emptyList()
    }

    suspend fun generatePinyinDistractors(
        correctPinyin: String,
        hanzi: String,
        box: Int,
        studyChars: Set<String> = emptySet(),
        targetOptionCount: Int = if (box >= 3) 6 else 4,
        vocabularyRepository: VocabularyRepository,
        dictionaryRepository: VocabularyRepository?
    ): List<String> {
        val pinyins = PinyinParser.parse_string_to_pinyin(correctPinyin)
        val firstPinyin = pinyins?.firstOrNull()
        val targetRadical = (firstPinyin?.radical ?: correctPinyin.filter { !it.isDigit() }).lowercase()

        val neededDistractorSyllables = (targetOptionCount - 1).coerceAtLeast(1)

        fun extractSyllable(raw: String): String? {
            val p = PinyinParser.parse_string_to_pinyin(raw)?.firstOrNull() ?: return null
            val rad = p.radical.lowercase()
            if (rad.isBlank() || rad == targetRadical) return null
            return rad
        }

        // 1. Gather candidate distractor syllables from visually similar characters
        val similarChars = (
            vocabularyRepository.getSimilarCharacters(hanzi, limit = 20) +
            (dictionaryRepository?.getSimilarCharacters(hanzi, limit = 20) ?: emptyList())
        ).filter { it != hanzi }.distinct()

        val knownSimilarChars = similarChars.filter { studyChars.contains(it) }
        val unstudiedSimilarChars = similarChars.filter { !studyChars.contains(it) }

        val maxSimilarSyllables = when {
            box <= 1 -> (0..minOf(1, neededDistractorSyllables)).random() // Box 0-1: 0 or 1
            box in 2..4 -> (1..minOf(2, neededDistractorSyllables)).random() // Box 2-4: 1 or 2
            else -> (2..neededDistractorSyllables).random() // Box 5+: 2 to 3 from similar characters
        }

        val chosenDistractorSyllables = mutableSetOf<String>()

        if (maxSimilarSyllables > 0) {
            val charsToQuery = if (box >= 5) {
                (knownSimilarChars.shuffled() + unstudiedSimilarChars.shuffled()).take(maxSimilarSyllables * 2)
            } else {
                knownSimilarChars.shuffled().take(maxSimilarSyllables * 2)
            }

            val pinyinsFromVoc = if (charsToQuery.isNotEmpty()) {
                vocabularyRepository.getPinyinsForHanziList(charsToQuery)
            } else emptyList()

            val pinyinsFromDict = if (box >= 5 && charsToQuery.isNotEmpty() && dictionaryRepository != null) {
                dictionaryRepository.getPinyinsForHanziList(charsToQuery)
            } else emptyList()

            for (p in (pinyinsFromVoc + pinyinsFromDict).shuffled()) {
                if (chosenDistractorSyllables.size >= maxSimilarSyllables) break
                val syll = extractSyllable(p) ?: continue
                chosenDistractorSyllables.add(syll)
            }
        }

        // 2. Backfill syllables from general study vocabulary if needed
        if (chosenDistractorSyllables.size < neededDistractorSyllables) {
            val studyPinyins = vocabularyRepository.getAllDistinctPinyins(100).shuffled()
            for (p in studyPinyins) {
                if (chosenDistractorSyllables.size >= neededDistractorSyllables) break
                val syll = extractSyllable(p) ?: continue
                chosenDistractorSyllables.add(syll)
            }
        }

        // 3. Backfill syllables from dictionary repository or default fallback list
        if (chosenDistractorSyllables.size < neededDistractorSyllables && dictionaryRepository != null) {
            val dictPinyins = dictionaryRepository.getAllDistinctPinyins(100).shuffled()
            for (p in dictPinyins) {
                if (chosenDistractorSyllables.size >= neededDistractorSyllables) break
                val syll = extractSyllable(p) ?: continue
                chosenDistractorSyllables.add(syll)
            }
        }

        if (chosenDistractorSyllables.size < neededDistractorSyllables) {
            val fallbackPinyins = DEFAULT_FALLBACK_PINYIN_DISTRACTORS.shuffled()
            for (p in fallbackPinyins) {
                if (chosenDistractorSyllables.size >= neededDistractorSyllables) break
                val syll = extractSyllable(p) ?: continue
                chosenDistractorSyllables.add(syll)
            }
        }

        val nonTargetOptions = (chosenDistractorSyllables - targetRadical).shuffled()
        val finalOptions = (listOf(targetRadical) + nonTargetOptions.take(neededDistractorSyllables)).shuffled()
        return finalOptions
    }

    fun generateEnglishDistractors(
        correct: String,
        allStudyEnglish: Set<String> = emptySet(),
        excludedMeanings: Collection<String> = emptyList()
    ): List<String> {
        val excludedLower = (excludedMeanings + correct).map { it.trim().lowercase() }.toSet()
        val candidates = allStudyEnglish
            .filter { it.isNotBlank() && it.trim().lowercase() !in excludedLower }
            .toMutableList()

        if (candidates.size < 3) {
            val fallback = DEFAULT_FALLBACK_ENGLISH_DISTRACTORS.filter {
                it.isNotBlank() &&
                it.trim().lowercase() !in excludedLower &&
                candidates.none { c -> c.equals(it, ignoreCase = true) }
            }.shuffled()
            candidates.addAll(fallback)
        }

        val distractors = candidates.shuffled().take(3).toMutableList()
        distractors.add(correct)
        return distractors.shuffled()
    }

    suspend fun generateClozeDistractors(
        correct: String,
        missingCharBox: Int,
        count: Int = 3,
        studyChars: Set<String> = emptySet(),
        vocabularyRepository: VocabularyRepository
    ): List<String> {
        val similarFromCedict = vocabularyRepository.getSimilarCharacters(correct, limit = 20)
            .filter { it != correct }

        val knownSimilar = similarFromCedict.filter { it in studyChars }
        val unstudiedSimilar = similarFromCedict.filter { it !in studyChars }
        val otherStudyChars = studyChars.filter { it != correct && it !in knownSimilar }.shuffled()

        val numSimilarTarget = when {
            missingCharBox <= 1 -> (0..minOf(count, if (missingCharBox == 0) 1 else 2)).random()
            missingCharBox in 2..3 -> (1..minOf(count, 2)).random()
            missingCharBox == 4 -> (1..minOf(count, 3)).random()
            else -> (1..count).random()
        }

        val selectedDistractors = mutableListOf<String>()

        if (missingCharBox >= 5) {
            val similarPool = (knownSimilar.shuffled() + unstudiedSimilar.shuffled())
            selectedDistractors.addAll(similarPool.take(numSimilarTarget))
            if (selectedDistractors.size < count) {
                selectedDistractors.addAll(otherStudyChars.take(count - selectedDistractors.size))
            }
            if (selectedDistractors.size < count) {
                val remainingUnstudied = unstudiedSimilar.filter { it !in selectedDistractors }
                selectedDistractors.addAll(remainingUnstudied.take(count - selectedDistractors.size))
            }
        } else {
            selectedDistractors.addAll(knownSimilar.shuffled().take(numSimilarTarget))
            if (selectedDistractors.size < count) {
                selectedDistractors.addAll(otherStudyChars.take(count - selectedDistractors.size))
            }
        }

        val finalTiles = selectedDistractors.take(count).toMutableList()
        finalTiles.add(correct)
        return finalTiles.shuffled()
    }

    suspend fun generateCharDistractorsForWord(
        wordSlots: List<String>,
        wordBox: Int,
        count: Int = 3,
        studyChars: Set<String> = emptySet(),
        vocabularyRepository: VocabularyRepository,
        dictionaryRepository: VocabularyRepository?,
        studyRepository: StudyRepository
    ): List<String> {
        val existingChars = wordSlots.toSet()
        val distinctChars = wordSlots.distinct().shuffled()
        val selectedDistractors = mutableSetOf<String>()

        for (c in distinctChars) {
            if (selectedDistractors.size >= count) break

            val charBox = studyRepository.getLeitnerState(c)?.box ?: wordBox

            val similarFromCedict = (
                vocabularyRepository.getSimilarCharacters(c, limit = 20) +
                (dictionaryRepository?.getSimilarCharacters(c, limit = 20) ?: emptyList())
            ).filter { it !in existingChars && it !in selectedDistractors }.distinct()

            val knownSimilar = similarFromCedict.filter { it in studyChars }
            val unstudiedSimilar = similarFromCedict.filter { it !in studyChars }

            val remainingSlots = count - selectedDistractors.size
            val numToTakeForChar = when {
                charBox <= 1 -> (0..minOf(1, remainingSlots)).random()
                charBox in 2..4 -> (1..minOf(2, remainingSlots)).random()
                else -> (1..minOf(3, remainingSlots)).random()
            }

            if (numToTakeForChar > 0) {
                if (charBox >= 5) {
                    val pool = (knownSimilar.shuffled() + unstudiedSimilar.shuffled())
                    selectedDistractors.addAll(pool.take(numToTakeForChar))
                } else {
                    selectedDistractors.addAll(knownSimilar.shuffled().take(numToTakeForChar))
                }
            }
        }

        if (selectedDistractors.size < count) {
            val otherStudyChars = studyChars.filter { it !in existingChars && it !in selectedDistractors }.shuffled()
            selectedDistractors.addAll(otherStudyChars.take(count - selectedDistractors.size))
        }

        val anyCharWellKnown = distinctChars.any { (studyRepository.getLeitnerState(it)?.box ?: wordBox) >= 5 }
        if (selectedDistractors.size < count && anyCharWellKnown) {
            val remainingUnstudied = distinctChars.flatMap { c ->
                val b = studyRepository.getLeitnerState(c)?.box ?: wordBox
                if (b >= 5) {
                    (vocabularyRepository.getSimilarCharacters(c, limit = 20) +
                     (dictionaryRepository?.getSimilarCharacters(c, limit = 20) ?: emptyList()))
                        .filter { it !in studyChars && it !in existingChars && it !in selectedDistractors }
                } else emptyList()
            }.distinct().shuffled()
            selectedDistractors.addAll(remainingUnstudied.take(count - selectedDistractors.size))
        }

        return selectedDistractors.take(count).toList().shuffled()
    }
}

