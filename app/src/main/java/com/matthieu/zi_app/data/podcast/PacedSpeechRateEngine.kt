package com.matthieu.zi_app.data.podcast

import com.matthieu.zi_app.data.local.entity.PodcastSentenceEntity

/**
 * Paced Speech Rate Engine.
 *
 * Decomposes raw podcast episode transcripts into natural sentence units and
 * estimates start_ms and end_ms per sentence using character density, punctuation pauses,
 * and channel/episode-specific intro & outro timing offsets.
 */
object PacedSpeechRateEngine {

    // Weights for speech pacing calculation
    const val WEIGHT_CHAR = 1.0
    const val WEIGHT_PAUSE = 1.5   // ， 、 ；
    const val WEIGHT_PERIOD = 3.0  // 。 ！ ？ \n

    private val TERMINATORS = setOf('。', '！', '？', '!', '?', '\n')
    private val CLOSING_QUOTES = setOf('”', '’', '"', '\'', ')', '）', '》', '」', '』')
    private val PAUSE_MARKS = setOf('，', ',', '、', '；', ';', '：', ':')

    /**
     * Splits raw transcript text into natural sentence units.
     * Preserves sentence-ending punctuation and trailing quotation marks/brackets.
     */
    fun splitTranscriptIntoSentences(transcript: String): List<String> {
        if (transcript.isBlank()) return emptyList()

        val sentences = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0

        while (i < transcript.length) {
            val char = transcript[i]
            current.append(char)
            if (char in TERMINATORS) {
                // Attach any immediately following closing quotes or brackets to the sentence
                while (i + 1 < transcript.length && transcript[i + 1] in CLOSING_QUOTES) {
                    i++
                    current.append(transcript[i])
                }
                val trimmed = current.toString().trim()
                if (trimmed.isNotBlank()) {
                    sentences.add(trimmed)
                }
                current.clear()
            }
            i++
        }

        val remaining = current.toString().trim()
        if (remaining.isNotBlank()) {
            sentences.add(remaining)
        }

        return sentences
    }

    /**
     * Computes the speech weight for a sentence based on character count and punctuation pauses.
     */
    fun calculateSentenceWeight(sentence: String): Double {
        var weight = 0.0
        for (char in sentence) {
            when {
                char in TERMINATORS -> weight += WEIGHT_PERIOD
                char in PAUSE_MARKS -> weight += WEIGHT_PAUSE
                char.isWhitespace() -> Unit
                else -> weight += WEIGHT_CHAR
            }
        }
        return if (weight > 0.0) weight else 1.0
    }

    /**
     * Estimates start and end timestamps for each sentence in an episode.
     *
     * @param episodeId Database ID of the episode.
     * @param transcript Raw text of the episode.
     * @param totalDurationMs Total audio duration in milliseconds.
     * @param introOffsetMs Spoken intro duration in milliseconds (where story text begins).
     * @param outroOffsetMs Spoken outro duration in milliseconds (after story text ends).
     * @return List of PodcastSentenceEntity items with calculated startMs and endMs.
     */
    fun computeSentenceTimestamps(
        episodeId: Int,
        transcript: String,
        totalDurationMs: Int,
        introOffsetMs: Int,
        outroOffsetMs: Int
    ): List<PodcastSentenceEntity> {
        val rawSentences = splitTranscriptIntoSentences(transcript)
        if (rawSentences.isEmpty()) return emptyList()

        val effectiveDurationMs = maxOf(0, totalDurationMs - introOffsetMs - outroOffsetMs)

        // If audio duration is unknown or too short, assign baseline intervals (~3 seconds per sentence)
        if (effectiveDurationMs <= 0 || totalDurationMs <= 0) {
            var currentMs = introOffsetMs
            return rawSentences.mapIndexed { idx, sent ->
                val estimatedSentDuration = (sent.length * 400).coerceIn(1500, 8000)
                val start = currentMs
                val end = start + estimatedSentDuration
                currentMs = end
                PodcastSentenceEntity(
                    episodeId = episodeId,
                    sentenceIdx = idx,
                    chineseText = sent,
                    englishText = null,
                    startMs = start,
                    endMs = end
                )
            }
        }

        val weights = rawSentences.map { calculateSentenceWeight(it) }
        val totalWeight = weights.sum()

        var currentStartMs = introOffsetMs
        return rawSentences.mapIndexed { idx, sent ->
            val sentenceWeight = weights[idx]
            val durationMs = if (totalWeight > 0) {
                ((sentenceWeight / totalWeight) * effectiveDurationMs).toInt()
            } else {
                effectiveDurationMs / rawSentences.size
            }

            val start = currentStartMs
            val end = if (idx == rawSentences.lastIndex) {
                // Ensure the last sentence ends exactly before the outro
                totalDurationMs - outroOffsetMs
            } else {
                start + durationMs
            }
            currentStartMs = end

            PodcastSentenceEntity(
                episodeId = episodeId,
                sentenceIdx = idx,
                chineseText = sent,
                englishText = null,
                startMs = start,
                endMs = maxOf(start, end)
            )
        }
    }

    /**
     * Resolves the intro and outro offsets for an episode based on channel and episode metadata.
     */
    fun resolveOffsets(
        channelId: Int,
        episodeNum: Int?,
        channelFallbackIntroMs: Int,
        channelFallbackOutroMs: Int
    ): EpisodeTimingOffset {
        return when (channelId) {
            1 -> SlowChineseOffsets.getOffset(episodeNum)
            2 -> ReadingChinaOffsets.getOffset(episodeNum)
            3 -> EpisodeTimingOffset(
                introOffsetMs = if (channelFallbackIntroMs > 0) channelFallbackIntroMs else 12000,
                outroOffsetMs = if (channelFallbackOutroMs > 0) channelFallbackOutroMs else 16000
            )
            else -> EpisodeTimingOffset(
                introOffsetMs = channelFallbackIntroMs,
                outroOffsetMs = channelFallbackOutroMs
            )
        }
    }
}

