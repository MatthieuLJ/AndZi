package com.matthieu.zi_app.data.podcast

import android.util.Xml
import com.matthieu.zi_app.data.local.entity.PodcastEpisodeEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

object PodcastRssParser {

    private val rfc822DateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
    private val htmlTagPattern = Pattern.compile("<[^>]+>")

    fun parseRss(inputStream: InputStream, defaultChannelId: Int = 1): List<PodcastEpisodeEntity> {
        val episodes = mutableListOf<PodcastEpisodeEntity>()

        val parser: XmlPullParser = try {
            Xml.newPullParser()
        } catch (e: Exception) {
            XmlPullParserFactory.newInstance().newPullParser()
        }

        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        var inItem = false

        var title: String? = null
        var audioUrl: String? = null
        var durationMs = 0
        var pubDate: Long = 0
        var description: String? = null
        var itunesSummary: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (tagName.equals("item", ignoreCase = true)) {
                        inItem = true
                        title = null
                        audioUrl = null
                        durationMs = 0
                        pubDate = 0
                        description = null
                        itunesSummary = null
                    } else if (inItem) {
                        when (tagName.lowercase()) {
                            "title" -> title = safeNextText(parser)
                            "enclosure" -> {
                                val url = parser.getAttributeValue(null, "url")
                                if (url != null) {
                                    audioUrl = when {
                                        url.startsWith("//") -> "https:$url"
                                        url.startsWith("http://archive.org") -> url.replaceFirst("http://", "https://")
                                        else -> url
                                    }
                                }
                            }
                            "duration", "itunes:duration" -> {
                                val durStr = safeNextText(parser)
                                durationMs = parseDurationToMs(durStr)
                            }
                            "pubdate" -> {
                                val dateStr = safeNextText(parser)
                                pubDate = parsePubDate(dateStr)
                            }
                            "summary", "itunes:summary" -> itunesSummary = safeNextText(parser)
                            "description" -> description = safeNextText(parser)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tagName.equals("item", ignoreCase = true)) {
                        inItem = false
                        val finalTitle = title ?: "Untitled Episode"
                        val finalAudioUrl = audioUrl ?: ""

                        // Determine channel: if title contains "中文天天读", map to channel 2, else defaultChannelId
                        val channelId = if (finalTitle.contains("中文天天读")) 2 else defaultChannelId

                        // Extract episode number from title
                        val episodeNum = extractEpisodeNum(finalTitle)

                        // Clean transcript (prefer itunes:summary which contains the Chinese text)
                        val rawText = if (!itunesSummary.isNullOrBlank()) itunesSummary else description ?: ""
                        var cleanTranscript = if (channelId == 2) {
                            cleanReadingChinaTranscript(rawText, finalTitle)
                        } else {
                            cleanTranscript(rawText)
                        }
                        if (cleanTranscript.isBlank()) {
                            SlowChineseTranscripts.getPatchedTranscript(channelId, episodeNum)?.let {
                                cleanTranscript = it
                            }
                        }

                        episodes.add(
                            PodcastEpisodeEntity(
                                episodeId = 0, // autoGenerate by Room
                                channelId = channelId,
                                episodeNum = episodeNum,
                                title = finalTitle,
                                audioUrl = finalAudioUrl,
                                durationMs = durationMs,
                                transcript = cleanTranscript,
                                pubDate = pubDate,
                                playbackPositionMs = 0,
                                isCompleted = false,
                                downloadedPath = null
                            )
                        )
                    }
                }
            }
            eventType = parser.next()
        }

        return episodes
    }

    private fun extractEpisodeNum(title: String): Int {
        val matcher = Pattern.compile("(\\d+)").matcher(title)
        return if (matcher.find()) {
            matcher.group(1)?.toIntOrNull() ?: 0
        } else 0
    }

    private fun parseDurationToMs(durationStr: String?): Int {
        if (durationStr.isNullOrBlank()) return 0
        return try {
            val parts = durationStr.trim().split(":")
            when (parts.size) {
                3 -> {
                    val h = parts[0].toDoubleOrNull() ?: 0.0
                    val m = parts[1].toDoubleOrNull() ?: 0.0
                    val s = parts[2].toDoubleOrNull() ?: 0.0
                    ((h * 3600 + m * 60 + s) * 1000).toInt()
                }
                2 -> {
                    val m = parts[0].toDoubleOrNull() ?: 0.0
                    val s = parts[1].toDoubleOrNull() ?: 0.0
                    ((m * 60 + s) * 1000).toInt()
                }
                else -> {
                    val s = durationStr.toDoubleOrNull() ?: 0.0
                    (s * 1000).toInt()
                }
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun parsePubDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0
        return try {
            rfc822DateFormat.parse(dateStr.trim())?.time ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun cleanTranscript(rawHtml: String): String {
        var text = htmlTagPattern.matcher(rawHtml).replaceAll("")
        text = text.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        return lines.joinToString("\n\n")
    }

    /**
     * Cleans transcripts for Reading China (中文天天读, Channel 2).
     *
     * The RSS feed descriptions for Reading China contain:
     * 1. An optional leading lesson title line (which is already read during the intro audio).
     * 2. The spoken story dialogue.
     * 3. Vocabulary notes ("词语 Words and Expressions") and grammar explanations ("语言点 Language Points").
     *
     * In the audio, the speaker only reads the story text (150-250 chars) over 40-80 seconds.
     * This function cuts off vocabulary/grammar notes and strips leading title duplicates,
     * restoring natural pacing (~4.2-4.8 chars/sec) and matching spoken audio.
     */
    fun cleanReadingChinaTranscript(rawHtml: String, title: String): String {
        var text = htmlTagPattern.matcher(rawHtml).replaceAll(" ")
        text = text.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&apos;", "'")
            .replace("&quot;", "\"")

        // Truncate at vocabulary and grammar notes
        val vocabMatcher = Pattern.compile(
            "(?i)(?:\\n|\\r|\\A)\\s*(?:词语(?:\\s*Words and Expressions)?|生词|语言点(?:\\s*Language Points)?|Words and Expressions|Language Points|注释\\s*Notes)",
            Pattern.CASE_INSENSITIVE
        ).matcher(text)
        if (vocabMatcher.find()) {
            text = text.substring(0, vocabMatcher.start())
        }

        // Extract lesson title (e.g. from "中文天天读 1 - “安”的意思是什么？" -> "“安”的意思是什么？")
        var lessonTitle = title
        if (title.contains("-")) {
            lessonTitle = title.substringAfter("-").trim()
        } else if (title.contains("中文天天读")) {
            lessonTitle = title.replace(Regex("中文天天读\\s*\\d*\\s*"), "").trim()
        }

        var trimmedText = text.trim()
        val cleanLt = lessonTitle.trim('“', '”', '"', '\'', '‘', '’', '!', '！', '?', '？')
        val candidates = mutableListOf<String>()
        if (lessonTitle.isNotBlank()) candidates.add(lessonTitle)
        if (cleanLt.isNotBlank()) candidates.add(cleanLt)
        if (cleanLt.startsWith("你")) candidates.add(cleanLt.substring(1))

        for (cand in candidates) {
            val pat = Pattern.compile("^[“\"‘’]?${Pattern.quote(cand)}[”\"‘’]?[\\s!！?？:：]*")
            val m = pat.matcher(trimmedText)
            if (m.find()) {
                trimmedText = trimmedText.substring(m.end()).trim()
                break
            }
        }

        // Strip any remaining leading punctuation
        trimmedText = trimmedText.replace(Regex("^[\\s!！?？:：,，。]+"), "").trim()

        val lines = trimmedText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        return lines.joinToString("\n\n")
    }

    private fun safeNextText(parser: XmlPullParser): String {
        return try {
            parser.nextText()
        } catch (e: Exception) {
            ""
        }
    }
}

