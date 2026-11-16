package com.matthieu.zi_app.domain.study.quiz

import com.matthieu.zi_app.PinyinParser
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.toCodePointStrings
import com.matthieu.zi_app.domain.study.AdaptiveQuizPolicy
import com.matthieu.zi_app.ui.study.CollocationPair
import com.matthieu.zi_app.ui.study.QuizType
import com.matthieu.zi_app.ui.study.StudyCard

object ToneQuizStrategy : QuizStrategy {
    override val quizType = QuizType.TONE

    override fun isEligible(context: QuizEligibilityContext): Boolean {
        return context.isSingleChar && context.hasValidNonNeutralTone && context.box < 6
    }

    override suspend fun buildPayload(
        target: Vocabulary,
        context: QuizBuildContext
    ): QuizPayload.Tone {
        val hanzi = target.chinese
        val exampleWords = context.vocabularyRepository.getCompoundWordsForCharPinyin(hanzi, target.pinyin, limit = 4)
        val contextWords = exampleWords.filter { it != hanzi && it.codePointCount(0, it.length) > 1 }
        val tone = PinyinParser.parse_string_to_pinyin(target.pinyin)?.firstOrNull()?.tone ?: 1
        return QuizPayload.Tone(tone = tone, contextWords = contextWords)
    }

    override fun getPromptText(card: StudyCard, isRevealed: Boolean): String {
        return if (!isRevealed) "Swipe or tap the correct tone" else ""
    }
}

object PinyinQuizStrategy : QuizStrategy {
    override val quizType = QuizType.PINYIN

    override fun isEligible(context: QuizEligibilityContext): Boolean {
        return context.isSingleChar
    }

    override suspend fun buildPayload(
        target: Vocabulary,
        context: QuizBuildContext
    ): QuizPayload.Pinyin {
        val hanzi = target.chinese
        val exampleWords = context.vocabularyRepository.getCompoundWordsForCharPinyin(hanzi, target.pinyin, limit = 4)
        val contextWords = exampleWords.filter { it != hanzi && it.codePointCount(0, it.length) > 1 }
        val pinyinObj = PinyinParser.parse_string_to_pinyin(target.pinyin)?.firstOrNull()
        val syllable = (pinyinObj?.radical ?: target.pinyin.filter { !it.isDigit() }).lowercase()
        val tone = pinyinObj?.tone ?: 0
        val options = QuizDistractorGenerator.generatePinyinDistractors(
            correctPinyin = target.pinyin,
            hanzi = hanzi,
            box = context.box,
            studyChars = context.studyChars,
            vocabularyRepository = context.vocabularyRepository,
            dictionaryRepository = context.dictionaryRepository
        )

        val matchingDirectWords = context.vocabularyRepository.getCompoundWordsForCharPinyin(hanzi, target.pinyin, limit = 50).toSet()

        val matchingCompounds = context.containingWords.filter { word ->
            word.chinese != hanzi && (word.chinese in matchingDirectWords || wordUsesPinyinForChar(word, hanzi, target.pinyin))
        }

        val enrichedCompounds = matchingCompounds.map { word ->
            if (word.english.isEmpty() && context.dictionaryRepository != null) {
                val dictVoc = context.dictionaryRepository.getVocabularyForChinese(word.chinese)
                    .firstOrNull { it.pinyin == word.pinyin && it.english.isNotEmpty() }
                    ?: context.dictionaryRepository.getVocabularyForChinese(word.chinese)
                        .firstOrNull { it.english.isNotEmpty() }
                if (dictVoc != null && dictVoc.english.isNotEmpty()) {
                    word.copy(english = dictVoc.english)
                } else word
            } else word
        }

        val selfWords = context.vocabularyRepository.getVocabularyForChinese(hanzi)
            .filter { it.english.isNotEmpty() && (it.pinyin == target.pinyin || wordUsesPinyinForChar(it, hanzi, target.pinyin)) }
            .ifEmpty {
                val dictEntry = if (target.english.isEmpty() && context.dictionaryRepository != null) {
                    context.dictionaryRepository.getVocabularyForChinese(hanzi)
                        .firstOrNull { it.pinyin == target.pinyin && it.english.isNotEmpty() }
                } else null
                val base = if (dictEntry != null) target.copy(english = dictEntry.english) else target
                if (base.english.isNotEmpty()) listOf(base) else emptyList()
            }

        val relatedStudyWords = (selfWords + enrichedCompounds)
            .distinctBy { "${it.chinese}_${it.pinyin}" }

        return QuizPayload.Pinyin(
            syllable = syllable,
            tone = tone,
            options = options,
            contextWords = contextWords,
            relatedStudyWords = relatedStudyWords
        )
    }

    override fun getPromptText(card: StudyCard, isRevealed: Boolean): String {
        return if (card.tone in 1..4) "Select Pinyin and tone" else "Select the correct Pinyin"
    }

    private fun wordUsesPinyinForChar(word: Vocabulary, targetChar: String, targetPinyin: String): Boolean {
        val parsedPinyins = PinyinParser.parse_string_to_pinyin(word.pinyin) ?: return false
        val charList = word.chinese.toCodePointStrings()
        if (charList.size != parsedPinyins.size) {
            val cleanTarget = targetPinyin.trim().lowercase()
            val radicalOnly = cleanTarget.filter { !it.isDigit() }
            val cleanWordPinyin = word.pinyin.trim().lowercase()
            return cleanWordPinyin.contains(cleanTarget) || cleanWordPinyin.contains(radicalOnly)
        }

        val targetParsed = PinyinParser.parse_string_to_pinyin(targetPinyin)?.firstOrNull()
        val targetLame = targetParsed?.lame?.lowercase() ?: targetPinyin.trim().lowercase()
        val targetRadical = targetParsed?.radical?.lowercase() ?: targetPinyin.filter { !it.isDigit() }.lowercase()
        val targetTone = targetParsed?.tone ?: 0

        for (i in charList.indices) {
            if (charList[i] == targetChar) {
                val syl = parsedPinyins[i]
                if (syl.lame.equals(targetLame, ignoreCase = true)) return true
                if (syl.radical.equals(targetRadical, ignoreCase = true) && (targetTone == 0 || syl.tone == targetTone)) return true
            }
        }
        return false
    }
}

object EnglishQuizStrategy : QuizStrategy {
    override val quizType = QuizType.ENGLISH

    override fun isEligible(context: QuizEligibilityContext): Boolean {
        return context.hasUserEnglish
    }

    override suspend fun buildPayload(
        target: Vocabulary,
        context: QuizBuildContext
    ): QuizPayload.EnglishMcq {
        val correct = target.english.randomOrNull() ?: "Meaning"
        val options = QuizDistractorGenerator.generateEnglishDistractors(
            correct = correct,
            allStudyEnglish = context.allStudyEnglish,
            excludedMeanings = target.english
        )
        return QuizPayload.EnglishMcq(options = options, correctMeanings = target.english)
    }

    override fun getPromptText(card: StudyCard, isRevealed: Boolean): String {
        return "Select the English meaning"
    }
}

object ClozeQuizStrategy : QuizStrategy {
    override val quizType = QuizType.CLOZE

    override fun isEligible(context: QuizEligibilityContext): Boolean {
        return if (context.isSingleChar) context.hasContainingWords else true
    }

    override suspend fun buildPayload(
        target: Vocabulary,
        context: QuizBuildContext
    ): QuizPayload.Cloze {
        val hanzi = target.chinese
        val charCount = hanzi.codePointCount(0, hanzi.length)
        val isSingleChar = charCount == 1

        var cardEnglish = target.english
        var displayPinyin = target.pinyin
        val wordSlots = mutableListOf<String>()
        val scrambledTiles = mutableListOf<String>()
        var targetIdx = -1

        val distractorCount = QuizDistractorGenerator.getDistractorCountForBox(context.box, QuizType.CLOZE)

        if (isSingleChar && context.containingWords.isNotEmpty()) {
            val comp = context.containingWords[context.index % context.containingWords.size]
            val compHanzi = comp.chinese
            wordSlots.addAll(compHanzi.toCodePointStrings())
            val idxInWord = wordSlots.indexOf(hanzi)
            targetIdx = if (idxInWord >= 0) idxInWord else 0
            val correctChar = wordSlots[targetIdx]
            val missingCharBox = context.studyRepository.getLeitnerState(correctChar)?.box ?: context.box
            scrambledTiles.addAll(
                QuizDistractorGenerator.generateClozeDistractors(
                    correctChar,
                    missingCharBox,
                    distractorCount,
                    context.studyChars,
                    context.vocabularyRepository,
                    context.dictionaryRepository
                )
            )
            if (comp.english.isNotEmpty()) {
                cardEnglish = comp.english
            }
            if (comp.pinyin.isNotBlank()) {
                displayPinyin = comp.pinyin
            }
        } else {
            wordSlots.addAll(hanzi.toCodePointStrings())
            if (wordSlots.isNotEmpty()) {
                targetIdx = (0 until wordSlots.size).random()
                val correctChar = wordSlots[targetIdx]
                val missingCharBox = context.studyRepository.getLeitnerState(correctChar)?.box ?: context.box
                scrambledTiles.addAll(
                    QuizDistractorGenerator.generateClozeDistractors(
                        correctChar,
                        missingCharBox,
                        distractorCount,
                        context.studyChars,
                        context.vocabularyRepository,
                        context.dictionaryRepository
                    )
                )
            }
        }

        if (cardEnglish.isEmpty() && context.dictionaryRepository != null) {
            val dictVoc = context.dictionaryRepository.getVocabularyForChinese(hanzi).firstOrNull { it.english.isNotEmpty() }
            if (dictVoc != null && dictVoc.english.isNotEmpty()) {
                cardEnglish = dictVoc.english
            }
        }

        val missingChar = if (targetIdx in wordSlots.indices) wordSlots[targetIdx] else hanzi
        val revealedWord = if (wordSlots.isNotEmpty()) wordSlots.joinToString("") else hanzi
        val relatedStudyWords = (
            context.vocabularyRepository.getVocabularyForChinese(missingChar).filter { it.english.isNotEmpty() } +
            context.vocabularyRepository.getWordsContainingCharacter(missingChar)
        )
            .distinctBy { "${it.chinese}_${it.pinyin}" }
            .sortedBy { if (it.chinese == revealedWord) 1 else 0 }

        return QuizPayload.Cloze(
            slots = wordSlots,
            targetIndex = targetIdx,
            tiles = scrambledTiles,
            displayPinyin = displayPinyin,
            cardEnglish = cardEnglish,
            relatedStudyWords = relatedStudyWords
        )
    }

    override fun getPromptText(card: StudyCard, isRevealed: Boolean): String {
        return "Fill in the missing character"
    }
}

object WordAssemblyQuizStrategy : QuizStrategy {
    override val quizType = QuizType.WORD_ASSEMBLY

    override fun isEligible(context: QuizEligibilityContext): Boolean {
        return !context.isSingleChar
    }

    override suspend fun buildPayload(
        target: Vocabulary,
        context: QuizBuildContext
    ): QuizPayload.WordAssembly {
        val hanzi = target.chinese
        val wordSlots = mutableListOf<String>()
        val scrambledTiles = mutableListOf<String>()

        var charIdx = 0
        while (charIdx < hanzi.length) {
            val codePoint = hanzi.codePointAt(charIdx)
            val ch = String(Character.toChars(codePoint))
            wordSlots.add(ch)
            charIdx += Character.charCount(codePoint)
        }

        val distractorCount = QuizDistractorGenerator.getDistractorCountForBox(context.box, QuizType.WORD_ASSEMBLY)
        val distractors = QuizDistractorGenerator.generateCharDistractorsForWord(
            wordSlots = wordSlots,
            wordBox = context.box,
            count = distractorCount,
            studyChars = context.studyChars,
            vocabularyRepository = context.vocabularyRepository,
            dictionaryRepository = context.dictionaryRepository,
            studyRepository = context.studyRepository
        )
        val uniqueTiles = (wordSlots.distinct() + distractors).distinct().shuffled()
        scrambledTiles.addAll(uniqueTiles)

        return QuizPayload.WordAssembly(
            slots = wordSlots,
            tiles = scrambledTiles,
            displayPinyin = target.pinyin,
            cardEnglish = target.english
        )
    }

    override fun getPromptText(card: StudyCard, isRevealed: Boolean): String {
        return "Assemble the compound word"
    }
}

object WritingQuizStrategy : QuizStrategy {
    override val quizType = QuizType.WRITING

    override fun isEligible(context: QuizEligibilityContext): Boolean {
        val strokes = context.strokeCount ?: return false
        return context.isSingleChar && context.box >= 1 && context.box >= AdaptiveQuizPolicy.minBoxForWriting(strokes)
    }

    override suspend fun buildPayload(
        target: Vocabulary,
        context: QuizBuildContext
    ): QuizPayload.Writing {
        val hanzi = target.chinese
        val charStrokes = context.strokeData
            ?: error("WritingQuizStrategy requires non-null strokeData for $hanzi")

        val eligibleContainingWords = context.containingWords.filter { comp ->
            QuizDistractorGenerator.countCharacterOccurrences(comp.chinese, hanzi) == 1
        }

        val wordSlots = mutableListOf<String>()
        var targetIdx = -1
        var cardEnglish = target.english
        var displayPinyin = target.pinyin

        if (eligibleContainingWords.isNotEmpty()) {
            val comp = eligibleContainingWords[context.index % eligibleContainingWords.size]
            val compHanzi = comp.chinese
            var charIdx = 0
            while (charIdx < compHanzi.length) {
                val cp = compHanzi.codePointAt(charIdx)
                wordSlots.add(String(Character.toChars(cp)))
                charIdx += Character.charCount(cp)
            }
            val idxInWord = wordSlots.indexOf(hanzi)
            targetIdx = if (idxInWord >= 0) idxInWord else 0
            if (comp.english.isNotEmpty()) {
                cardEnglish = comp.english
            }
            if (comp.pinyin.isNotBlank()) {
                displayPinyin = comp.pinyin
            }
        }

        if (cardEnglish.isEmpty() && context.dictionaryRepository != null) {
            val dictVoc = context.dictionaryRepository.getVocabularyForChinese(hanzi).firstOrNull { it.english.isNotEmpty() }
            if (dictVoc != null && dictVoc.english.isNotEmpty()) {
                cardEnglish = dictVoc.english
            }
        }

        val missingChar = if (targetIdx in wordSlots.indices) wordSlots[targetIdx] else hanzi
        val revealedWord = if (wordSlots.isNotEmpty()) wordSlots.joinToString("") else hanzi
        val relatedStudyWords = (
            context.vocabularyRepository.getVocabularyForChinese(missingChar).filter { it.english.isNotEmpty() } +
            context.vocabularyRepository.getWordsContainingCharacter(missingChar)
        )
            .distinctBy { "${it.chinese}_${it.pinyin}" }
            .sortedBy { if (it.chinese == revealedWord) 1 else 0 }

        return QuizPayload.Writing(
            character = charStrokes,
            slots = wordSlots,
            targetIndex = targetIdx,
            displayPinyin = displayPinyin,
            cardEnglish = cardEnglish,
            relatedStudyWords = relatedStudyWords
        )
    }

    override fun getPromptText(card: StudyCard, isRevealed: Boolean): String {
        return if (card.wordSlots.isNotEmpty()) "Write the missing character" else "Write the character from memory"
    }
}

object PairMatchQuizStrategy : QuizStrategy {
    override val quizType = QuizType.PAIR_MATCH

    override fun isEligible(context: QuizEligibilityContext): Boolean {
        return !context.isSingleChar && context.canPairMatch
    }

    override suspend fun buildPayload(
        target: Vocabulary,
        context: QuizBuildContext
    ): QuizPayload.PairMatch {
        val allVocList = listOf(target) + context.matchCompanions
        val matchPairs = allVocList.mapNotNull { v ->
            val split = QuizDistractorGenerator.splitTwoCharWord(v.chinese) ?: return@mapNotNull null
            CollocationPair(
                word = v.chinese,
                pinyin = v.pinyin,
                english = v.english,
                firstChar = split.first,
                secondChar = split.second
            )
        }
        val leftMatchTiles = matchPairs.map { it.firstChar }.shuffled()
        val rightMatchTiles = matchPairs.map { it.secondChar }.shuffled()

        return QuizPayload.PairMatch(
            pairs = matchPairs,
            leftTiles = leftMatchTiles,
            rightTiles = rightMatchTiles
        )
    }

    override fun getPromptText(card: StudyCard, isRevealed: Boolean): String {
        return "Match the characters to form words"
    }
}

object QuizStrategyRegistry {
    val allStrategies: List<QuizStrategy> = listOf(
        ToneQuizStrategy,
        PinyinQuizStrategy,
        EnglishQuizStrategy,
        ClozeQuizStrategy,
        WordAssemblyQuizStrategy,
        WritingQuizStrategy,
        PairMatchQuizStrategy
    )

    private val strategyMap: Map<QuizType, QuizStrategy> = allStrategies.associateBy { it.quizType }

    fun getStrategy(quizType: QuizType): QuizStrategy =
        strategyMap[quizType] ?: error("No strategy registered for $quizType")
}

