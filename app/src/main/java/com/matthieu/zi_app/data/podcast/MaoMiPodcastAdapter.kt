package com.matthieu.zi_app.data.podcast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

object MaoMiPodcastAdapter {

    const val CHANNEL_ID = 3
    const val DEFAULT_INTRO_OFFSET_MS = 12000
    const val DEFAULT_OUTRO_OFFSET_MS = 16000

    private const val WP_API_BASE = "https://maomichinese.com/wp-json/wp/v2/posts"
    private val CHINESE_CHAR_PATTERN = Pattern.compile("[\\u4E00-\\u9FA5]")
    private val HTML_TAG_PATTERN = Pattern.compile("<[^>]+>")

    /**
     * Extracts and cleans Chinese transcript paragraphs from WordPress rendered HTML content.
     */
    fun cleanTranscriptFromHtml(contentHtml: String): String {
        if (contentHtml.isBlank()) return ""

        // Strategy 1: Check for mme-lang-zh class
        val zhDivMatcher = Pattern.compile("<div[^>]*class=\"[^\"]*mme-lang-zh[^\"]*\"[^>]*>(.*?)</div>", Pattern.DOTALL)
            .matcher(contentHtml)
        if (zhDivMatcher.find()) {
            val text = extractParagraphsFromHtml(zhDivMatcher.group(1) ?: "")
            if (text.isNotBlank()) return text
        }

        // Strategy 2: Check for chinese-tab id
        val tabMatcher = Pattern.compile("<div[^>]*id=\"chinese-tab\"[^>]*>(.*?)</div>\\s*(?:<div id=|</div)", Pattern.DOTALL)
            .matcher(contentHtml)
        if (tabMatcher.find()) {
            val text = extractParagraphsFromHtml(tabMatcher.group(1) ?: "")
            if (text.isNotBlank()) return text
        }

        // Strategy 3: Fallback - collect all <p> tags with Chinese characters
        val allParas = extractParagraphsFromHtml(contentHtml)
        val filtered = allParas.split("\n\n").filter { p ->
            CHINESE_CHAR_PATTERN.matcher(p).find() &&
                !p.contains("MaoMi Chinese 是一个") &&
                !p.contains("支持我们") &&
                !p.contains("加入会员")
        }
        return filtered.joinToString("\n\n")
    }

    private fun extractParagraphsFromHtml(htmlChunk: String): String {
        val pMatcher = Pattern.compile("<p[^>]*>(.*?)</p>", Pattern.DOTALL).matcher(htmlChunk)
        val paras = mutableListOf<String>()

        while (pMatcher.find()) {
            val rawP = pMatcher.group(1) ?: ""
            var clean = HTML_TAG_PATTERN.matcher(rawP).replaceAll("").trim()
            clean = clean.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&apos;", "'")
                .replace("&quot;", "\"")
                .replace("&#8211;", "–")
                .replace("&#8217;", "'")
                .replace("&#8220;", "“")
                .replace("&#8221;", "”")
            val collapsed = clean.replace("\\s+".toRegex(), " ").trim()
            if (collapsed.isNotBlank() && collapsed != "\u00A0") {
                paras.add(collapsed)
            }
        }

        return paras.joinToString("\n\n")
    }

    /**
     * Dynamically fetches the transcript for an episode from the WordPress REST API.
     */
    suspend fun fetchTranscript(episodeNum: Int): String? = withContext(Dispatchers.IO) {
        try {
            // First attempt: slug lookup (fastest, exact match)
            val slugUrl = "$WP_API_BASE?slug=podcast$episodeNum"
            val textFromSlug = fetchContentFromUrl(slugUrl)
            if (!textFromSlug.isNullOrBlank()) {
                val parsed = cleanTranscriptFromHtml(textFromSlug)
                if (parsed.isNotBlank()) return@withContext parsed
            }

            // Fallback attempt: search by episode number
            val searchUrl = "$WP_API_BASE?search=%23$episodeNum"
            val textFromSearch = fetchContentFromUrl(searchUrl)
            if (!textFromSearch.isNullOrBlank()) {
                val parsed = cleanTranscriptFromHtml(textFromSearch)
                if (parsed.isNotBlank()) return@withContext parsed
            }

            null
        } catch (e: Exception) {
            android.util.Log.e("MaoMiPodcastAdapter", "Failed to fetch transcript for episode $episodeNum", e)
            null
        }
    }

    private fun fetchContentFromUrl(urlString: String): String? {
        val url = URL(urlString)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (AndZi Android)")
            setRequestProperty("Connection", "close")
        }

        if (connection.responseCode !in 200..299) {
            return null
        }

        val jsonString = connection.inputStream.use { stream ->
            BufferedReader(InputStreamReader(stream, "UTF-8")).use { reader ->
                reader.readText()
            }
        }

        val array = JSONArray(jsonString)
        if (array.length() == 0) return null

        val post = array.getJSONObject(0)
        val contentObj = post.optJSONObject("content")
        return contentObj?.optString("rendered")
    }
}
