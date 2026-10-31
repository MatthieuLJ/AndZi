package com.matthieu.zi_app.domain.study

import com.matthieu.zi_app.ui.study.QuizType

object AdaptiveQuizPolicy {

    /**
     * Determines the minimum Leitner box required before a character is eligible
     * for [QuizType.WRITING] based on its stroke complexity.
     *
     * Complexity tiers:
     * - 1 to 3 strokes (e.g., 一, 人, 十, 口): Box 1+
     * - 4 to 6 strokes (e.g., 中, 文, 你, 好): Box 2+
     * - 7 to 9 strokes (e.g., 我, 明, 学): Box 3+
     * - 10 to 12 strokes (e.g., 家, 读, 钱): Box 4+
     * - 13+ strokes (e.g., 像, 懂, 囊, 藏): Box 5+
     */
    fun minBoxForWriting(strokeCount: Int): Int {
        return when {
            strokeCount <= 3 -> 1
            strokeCount <= 6 -> 2
            strokeCount <= 9 -> 3
            strokeCount <= 12 -> 4
            else -> 5
        }
    }

    /**
     * Returns candidate quiz types for multi-character compound words.
     * Multi-character words never use TONE or WRITING.
     */
    fun getCandidatesForMultiChar(
        box: Int,
        hasUserEnglish: Boolean
    ): List<QuizType> {
        return if (hasUserEnglish) {
            when {
                box < 4 -> listOf(QuizType.WORD_ASSEMBLY, QuizType.CLOZE, QuizType.ENGLISH)
                else -> listOf(QuizType.WORD_ASSEMBLY, QuizType.CLOZE, QuizType.WORD_ASSEMBLY, QuizType.ENGLISH)
            }
        } else {
            when {
                box < 4 -> listOf(QuizType.WORD_ASSEMBLY, QuizType.CLOZE)
                else -> listOf(QuizType.WORD_ASSEMBLY, QuizType.CLOZE, QuizType.WORD_ASSEMBLY)
            }
        }
    }

    /**
     * Returns candidate quiz types for single characters based on Leitner box,
     * phonetics, translations, contextual words, and stroke count.
     *
     * Invariants:
     * 1. No WRITING in Box 0 (`box <= 0`).
     * 2. No TONE quiz at Box 6 or above (`box >= 6`).
     * 3. For Box 1..5, WRITING requires `box >= minBoxForWriting(strokeCount)`.
     */
    fun getCandidatesForSingleChar(
        box: Int,
        hasValidNonNeutralTone: Boolean,
        hasUserEnglish: Boolean,
        hasContainingWords: Boolean,
        strokeCount: Int?
    ): List<QuizType> {
        val canWrite = box >= 1 && strokeCount != null && box >= minBoxForWriting(strokeCount)
        val canTone = hasValidNonNeutralTone && box < 6
        val canCloze = hasContainingWords
        val canEnglish = hasUserEnglish

        return when {
            // Box 0: No writing under any circumstances. Phonetic/English recognition prioritized.
            box <= 0 -> {
                when {
                    canTone -> {
                        when {
                            canEnglish -> listOf(QuizType.TONE, QuizType.PINYIN, QuizType.ENGLISH)
                            canCloze -> listOf(QuizType.TONE, QuizType.CLOZE, QuizType.PINYIN)
                            else -> listOf(QuizType.TONE, QuizType.PINYIN)
                        }
                    }
                    canEnglish -> listOf(QuizType.ENGLISH, QuizType.PINYIN)
                    canCloze -> listOf(QuizType.CLOZE, QuizType.PINYIN)
                    else -> listOf(QuizType.PINYIN)
                }
            }

            // Box 1: First review. Only very simple writing (<= 3 strokes) unlocked.
            box == 1 -> {
                when {
                    canTone -> {
                        when {
                            canWrite && canEnglish -> listOf(QuizType.TONE, QuizType.PINYIN, QuizType.ENGLISH, QuizType.WRITING)
                            canWrite && canCloze -> listOf(QuizType.TONE, QuizType.CLOZE, QuizType.PINYIN, QuizType.WRITING)
                            canWrite -> listOf(QuizType.TONE, QuizType.PINYIN, QuizType.WRITING)
                            canEnglish -> listOf(QuizType.TONE, QuizType.ENGLISH, QuizType.PINYIN)
                            canCloze -> listOf(QuizType.TONE, QuizType.CLOZE, QuizType.PINYIN)
                            else -> listOf(QuizType.TONE, QuizType.PINYIN)
                        }
                    }
                    canWrite && canEnglish -> listOf(QuizType.ENGLISH, QuizType.PINYIN, QuizType.WRITING)
                    canWrite && canCloze -> listOf(QuizType.CLOZE, QuizType.PINYIN, QuizType.WRITING)
                    canWrite -> listOf(QuizType.PINYIN, QuizType.WRITING)
                    canEnglish -> listOf(QuizType.ENGLISH, QuizType.PINYIN)
                    canCloze -> listOf(QuizType.CLOZE, QuizType.PINYIN)
                    else -> listOf(QuizType.PINYIN)
                }
            }

            // Box 2-3: Developing. Moderate writing (<= 9 strokes) unlocked. Cloze & English emphasized.
            box in 2..3 -> {
                when {
                    canWrite -> {
                        when {
                            canTone && canEnglish -> listOf(QuizType.TONE, QuizType.ENGLISH, QuizType.WRITING, QuizType.PINYIN)
                            canTone && canCloze -> listOf(QuizType.TONE, QuizType.CLOZE, QuizType.WRITING, QuizType.PINYIN)
                            canTone -> listOf(QuizType.TONE, QuizType.WRITING, QuizType.PINYIN)
                            canEnglish -> listOf(QuizType.ENGLISH, QuizType.WRITING, QuizType.PINYIN)
                            canCloze -> listOf(QuizType.CLOZE, QuizType.WRITING, QuizType.PINYIN)
                            else -> listOf(QuizType.WRITING, QuizType.PINYIN)
                        }
                    }
                    canTone -> {
                        when {
                            canEnglish -> listOf(QuizType.TONE, QuizType.ENGLISH, QuizType.PINYIN)
                            canCloze -> listOf(QuizType.TONE, QuizType.CLOZE, QuizType.PINYIN)
                            else -> listOf(QuizType.TONE, QuizType.PINYIN)
                        }
                    }
                    canEnglish -> listOf(QuizType.ENGLISH, QuizType.PINYIN)
                    canCloze -> listOf(QuizType.CLOZE, QuizType.PINYIN)
                    else -> listOf(QuizType.PINYIN)
                }
            }

            // Box 4-5: Maturing. Higher stroke writing unlocked (up to 13+). Writing prioritized.
            box in 4..5 -> {
                when {
                    canWrite -> {
                        when {
                            canEnglish && canTone -> listOf(QuizType.WRITING, QuizType.ENGLISH, QuizType.WRITING, QuizType.PINYIN, QuizType.TONE)
                            canCloze && canTone -> listOf(QuizType.WRITING, QuizType.CLOZE, QuizType.WRITING, QuizType.PINYIN, QuizType.TONE)
                            canTone -> listOf(QuizType.WRITING, QuizType.PINYIN, QuizType.WRITING, QuizType.TONE)
                            canEnglish -> listOf(QuizType.WRITING, QuizType.ENGLISH, QuizType.WRITING, QuizType.PINYIN)
                            canCloze -> listOf(QuizType.WRITING, QuizType.CLOZE, QuizType.WRITING, QuizType.PINYIN)
                            else -> listOf(QuizType.WRITING, QuizType.WRITING, QuizType.PINYIN)
                        }
                    }
                    canTone -> {
                        when {
                            canEnglish -> listOf(QuizType.TONE, QuizType.ENGLISH, QuizType.PINYIN)
                            canCloze -> listOf(QuizType.TONE, QuizType.CLOZE, QuizType.PINYIN)
                            else -> listOf(QuizType.TONE, QuizType.PINYIN)
                        }
                    }
                    canEnglish -> listOf(QuizType.ENGLISH, QuizType.PINYIN)
                    canCloze -> listOf(QuizType.CLOZE, QuizType.PINYIN)
                    else -> listOf(QuizType.PINYIN)
                }
            }

            // Box 6+: Mastery. Strictly NO TONE quiz. Writing heavily prioritized.
            else -> {
                when {
                    canWrite -> {
                        when {
                            canEnglish -> listOf(QuizType.WRITING, QuizType.ENGLISH, QuizType.WRITING, QuizType.PINYIN)
                            canCloze -> listOf(QuizType.WRITING, QuizType.CLOZE, QuizType.WRITING, QuizType.PINYIN)
                            else -> listOf(QuizType.WRITING, QuizType.WRITING, QuizType.PINYIN)
                        }
                    }
                    canEnglish -> listOf(QuizType.ENGLISH, QuizType.PINYIN)
                    canCloze -> listOf(QuizType.CLOZE, QuizType.PINYIN)
                    else -> listOf(QuizType.PINYIN)
                }
            }
        }
    }

    /**
     * Selects the quiz type for a card given its parameters and session index.
     */
    fun selectQuizType(
        box: Int,
        isSingleChar: Boolean,
        hasValidNonNeutralTone: Boolean,
        hasUserEnglish: Boolean,
        hasContainingWords: Boolean,
        strokeCount: Int?,
        index: Int
    ): QuizType {
        val candidates = if (isSingleChar) {
            getCandidatesForSingleChar(
                box = box,
                hasValidNonNeutralTone = hasValidNonNeutralTone,
                hasUserEnglish = hasUserEnglish,
                hasContainingWords = hasContainingWords,
                strokeCount = strokeCount
            )
        } else {
            getCandidatesForMultiChar(
                box = box,
                hasUserEnglish = hasUserEnglish
            )
        }
        val safeIndex = if (index < 0) 0 else index
        return candidates[safeIndex % candidates.size]
    }
}

