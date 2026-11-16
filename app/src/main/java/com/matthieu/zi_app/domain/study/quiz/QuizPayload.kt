package com.matthieu.zi_app.domain.study.quiz

import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.domain.writing.HanziCharacter
import com.matthieu.zi_app.ui.study.CollocationPair

/**
 * Sealed hierarchy of immutable payloads containing data needed to display and
 * evaluate each quiz type.
 */
sealed interface QuizPayload {
    data object Empty : QuizPayload

    interface OptionsProvider { val options: List<String> }
    interface SlotsProvider { val slots: List<String> }
    interface TilesProvider { val tiles: List<String> }
    interface ClozeTargetProvider { val targetIndex: Int }
    interface ContextWordsProvider { val contextWords: List<String> }
    interface RelatedStudyWordsProvider { val relatedStudyWords: List<Vocabulary> }
    interface DisplayPinyinProvider { val displayPinyin: String }
    interface WritingProvider { val character: HanziCharacter? }
    interface MatchPairsProvider {
        val pairs: List<CollocationPair>
        val leftTiles: List<String>
        val rightTiles: List<String>
    }

    data class Tone(
        val tone: Int,
        override val contextWords: List<String> = emptyList()
    ) : QuizPayload, ContextWordsProvider

    data class Pinyin(
        val syllable: String,
        val tone: Int,
        override val options: List<String>,
        override val contextWords: List<String> = emptyList(),
        override val relatedStudyWords: List<Vocabulary> = emptyList()
    ) : QuizPayload, OptionsProvider, ContextWordsProvider, RelatedStudyWordsProvider

    data class EnglishMcq(
        override val options: List<String>,
        val correctMeanings: List<String>
    ) : QuizPayload, OptionsProvider

    data class Cloze(
        override val slots: List<String>,
        override val targetIndex: Int,
        override val tiles: List<String>,
        override val displayPinyin: String = "",
        val cardEnglish: List<String> = emptyList(),
        override val relatedStudyWords: List<Vocabulary> = emptyList()
    ) : QuizPayload, SlotsProvider, ClozeTargetProvider, TilesProvider, DisplayPinyinProvider, RelatedStudyWordsProvider

    data class WordAssembly(
        override val slots: List<String>,
        override val tiles: List<String>,
        override val displayPinyin: String = "",
        val cardEnglish: List<String> = emptyList()
    ) : QuizPayload, SlotsProvider, TilesProvider, DisplayPinyinProvider

    data class Writing(
        override val character: HanziCharacter,
        override val slots: List<String> = emptyList(),
        override val targetIndex: Int = -1,
        override val displayPinyin: String = "",
        val cardEnglish: List<String> = emptyList(),
        override val relatedStudyWords: List<Vocabulary> = emptyList()
    ) : QuizPayload, WritingProvider, SlotsProvider, ClozeTargetProvider, DisplayPinyinProvider, RelatedStudyWordsProvider

    data class PairMatch(
        override val pairs: List<CollocationPair>,
        override val leftTiles: List<String>,
        override val rightTiles: List<String>
    ) : QuizPayload, MatchPairsProvider
}

