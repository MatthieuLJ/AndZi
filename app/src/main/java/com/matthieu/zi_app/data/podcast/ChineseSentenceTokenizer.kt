package com.matthieu.zi_app.data.podcast

/**
 * Represents a token in a Chinese sentence (compound word, single character, punctuation, or whitespace).
 */
data class SentenceToken(
    val text: String,
    val startIndex: Int,
    val endIndex: Int,
    val isHanzi: Boolean,
    val isCompound: Boolean = false
)

object ChineseSentenceTokenizer {

    /**
     * Checks if a character is a Hanzi (CJK Unified Ideographs or Extension A).
     */
    fun isHanzi(c: Char): Boolean =
        (c in '\u4E00'..'\u9FA5') || (c in '\u3400'..'\u4DBF')

    /**
     * Splits a Chinese sentence into tokens using Forward Maximum Matching (FMM)
     * against a set of known vocabulary words.
     *
     * Non-Hanzi characters (punctuation, numbers, English) are grouped into non-Hanzi tokens.
     * Known compound words (length 4 down to 2) are extracted as compound tokens.
     * Any unmatched Hanzi character is emitted as an individual 1-character token.
     */
    fun tokenize(sentenceText: String, knownWords: Set<String> = emptySet()): List<SentenceToken> {
        if (sentenceText.isEmpty()) return emptyList()

        val tokens = mutableListOf<SentenceToken>()
        var i = 0
        val n = sentenceText.length

        while (i < n) {
            val c = sentenceText[i]

            // 1. Non-Hanzi (punctuation, whitespace, digits, latin characters)
            if (!isHanzi(c)) {
                val start = i
                while (i < n && !isHanzi(sentenceText[i])) {
                    i++
                }
                tokens.add(
                    SentenceToken(
                        text = sentenceText.substring(start, i),
                        startIndex = start,
                        endIndex = i,
                        isHanzi = false,
                        isCompound = false
                    )
                )
                continue
            }

            // 2. Hanzi: Try greedy matching against knownWords (length 4 down to 2)
            var matchedLen = 0
            val maxLen = minOf(4, n - i)
            for (len in maxLen downTo 2) {
                val candidate = sentenceText.substring(i, i + len)
                if (candidate in knownWords) {
                    matchedLen = len
                    tokens.add(
                        SentenceToken(
                            text = candidate,
                            startIndex = i,
                            endIndex = i + len,
                            isHanzi = true,
                            isCompound = true
                        )
                    )
                    break
                }
            }

            if (matchedLen > 0) {
                i += matchedLen
            } else {
                // Single Hanzi character
                tokens.add(
                    SentenceToken(
                        text = c.toString(),
                        startIndex = i,
                        endIndex = i + 1,
                        isHanzi = true,
                        isCompound = false
                    )
                )
                i += 1
            }
        }

        return tokens
    }
}
