package com.matthieu.zi_app.data.podcast

/**
 * Measured timing offsets for Reading China (中文天天读, Channel 2).
 *
 * Each episode features a short Chinese story.
 * - Episode 1 begins with a 12.8s series introduction and lesson title.
 * - Episodes 2-20 feature a 5.0s introductory jingle and lesson title announcement,
 *   after which the spoken story dialogue begins.
 * - All episodes conclude with approximately 6.0s of outro music.
 */
object ReadingChinaOffsets {

    val DEFAULT = EpisodeTimingOffset(
        introOffsetMs = 5000,
        outroOffsetMs = 6000
    )

    val OFFSETS: Map<Int, EpisodeTimingOffset> = mapOf(
        1 to EpisodeTimingOffset(introOffsetMs = 12800, outroOffsetMs = 5900, durationMs = 68000),
        2 to EpisodeTimingOffset(introOffsetMs = 4000, outroOffsetMs = 5900, durationMs = 39900),
        3 to EpisodeTimingOffset(introOffsetMs = 4000, outroOffsetMs = 7200, durationMs = 67200),
        4 to EpisodeTimingOffset(introOffsetMs = 5000, outroOffsetMs = 5900, durationMs = 48900),
        5 to EpisodeTimingOffset(introOffsetMs = 6000, outroOffsetMs = 7300, durationMs = 82300),
        6 to EpisodeTimingOffset(introOffsetMs = 5400, outroOffsetMs = 6100, durationMs = 71400),
        7 to EpisodeTimingOffset(introOffsetMs = 6000, outroOffsetMs = 6900, durationMs = 81900),
        8 to EpisodeTimingOffset(introOffsetMs = 5000, outroOffsetMs = 6200, durationMs = 73600),
        9 to EpisodeTimingOffset(introOffsetMs = 5000, outroOffsetMs = 6100, durationMs = 69700),
        10 to EpisodeTimingOffset(introOffsetMs = 4800, outroOffsetMs = 5900, durationMs = 78900),
        11 to EpisodeTimingOffset(introOffsetMs = 5000, outroOffsetMs = 6600, durationMs = 70600),
        12 to EpisodeTimingOffset(introOffsetMs = 5500, outroOffsetMs = 5200, durationMs = 71200),
        13 to EpisodeTimingOffset(introOffsetMs = 5000, outroOffsetMs = 6700, durationMs = 47700),
        14 to EpisodeTimingOffset(introOffsetMs = 5000, outroOffsetMs = 5900, durationMs = 73900),
        15 to EpisodeTimingOffset(introOffsetMs = 6000, outroOffsetMs = 5900, durationMs = 82200),
        16 to EpisodeTimingOffset(introOffsetMs = 5000, outroOffsetMs = 6000, durationMs = 63600),
        17 to EpisodeTimingOffset(introOffsetMs = 5000, outroOffsetMs = 6600, durationMs = 62600),
        18 to EpisodeTimingOffset(introOffsetMs = 5000, outroOffsetMs = 6500, durationMs = 73500),
        19 to EpisodeTimingOffset(introOffsetMs = 5000, outroOffsetMs = 5800, durationMs = 81800),
        20 to EpisodeTimingOffset(introOffsetMs = 5000, outroOffsetMs = 6500, durationMs = 74500)
    )

    fun getOffset(episodeNum: Int?): EpisodeTimingOffset {
        if (episodeNum == null) return DEFAULT
        return OFFSETS[episodeNum] ?: DEFAULT
    }
}
