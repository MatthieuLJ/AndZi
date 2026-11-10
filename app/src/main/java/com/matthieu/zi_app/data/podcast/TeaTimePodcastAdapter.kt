package com.matthieu.zi_app.data.podcast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

data class ParsedSrtCue(
    val index: Int,
    val startMs: Int,
    val endMs: Int,
    val text: String
)

object TeaTimePodcastAdapter {

    const val CHANNEL_ID = 4
    const val DEFAULT_INTRO_OFFSET_MS = 8000
    const val DEFAULT_OUTRO_OFFSET_MS = 10000

    private val TITLE_EP_NUM_PATTERN = Pattern.compile("第\\s*(\\d+)\\s*集")
    private val SLUG_EP_NUM_PATTERN = Pattern.compile("^\\s*(\\d+)")

    fun extractEpisodeNumber(title: String, slug: String? = null): Int {
        val titleMatcher = TITLE_EP_NUM_PATTERN.matcher(title)
        if (titleMatcher.find()) {
            return titleMatcher.group(1)?.toIntOrNull() ?: 0
        }
        if (!slug.isNullOrBlank()) {
            val slugMatcher = SLUG_EP_NUM_PATTERN.matcher(slug)
            if (slugMatcher.find()) {
                return slugMatcher.group(1)?.toIntOrNull() ?: 0
            }
        }
        val digitMatcher = Pattern.compile("(\\d+)").matcher(title)
        if (digitMatcher.find()) {
            return digitMatcher.group(1)?.toIntOrNull() ?: 0
        }
        return 0
    }

    fun getSrtUrlForEpisode(episodeNum: Int): String {
        val pad = if (episodeNum < 10) String.format("%02d", episodeNum) else episodeNum.toString()
        return "https://teatime2026.s3.us-east-1.amazonaws.com/teatimechinese/TTCN+$pad/TTCN+$pad.srt"
    }

    fun parseSrt(srtContent: String): List<ParsedSrtCue> {
        val cues = mutableListOf<ParsedSrtCue>()
        val blocks = srtContent.replace("\r\n", "\n").split(Regex("\n\\s*\n"))
        var cueIdx = 1

        for (b in blocks) {
            val lines = b.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue

            var timeLineIdx = -1
            for (i in lines.indices) {
                if (lines[i].contains("-->")) {
                    timeLineIdx = i
                    break
                }
            }
            if (timeLineIdx == -1) continue

            val timePart = lines[timeLineIdx]
            val timeParts = timePart.split("-->")
            if (timeParts.size != 2) continue

            val startMs = parseTime(timeParts[0])
            val endMs = parseTime(timeParts[1])
            val textLines = lines.drop(timeLineIdx + 1).filter { it != "[BREAK]" }
            val text = textLines.joinToString(" ").trim()
            if (text.isNotEmpty()) {
                cues.add(
                    ParsedSrtCue(
                        index = cueIdx++,
                        startMs = startMs,
                        endMs = endMs,
                        text = text
                    )
                )
            }
        }
        return cues
    }

    private fun parseTime(timeStr: String): Int {
        val parts = timeStr.trim().split(Regex("[:.,]"))
        if (parts.size < 4) return 0
        val h = parts[0].toIntOrNull() ?: 0
        val m = parts[1].toIntOrNull() ?: 0
        val s = parts[2].toIntOrNull() ?: 0
        val ms = parts[3].toIntOrNull() ?: 0
        return (h * 3600 + m * 60 + s) * 1000 + ms
    }

    suspend fun fetchSrt(srtUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL(srtUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
