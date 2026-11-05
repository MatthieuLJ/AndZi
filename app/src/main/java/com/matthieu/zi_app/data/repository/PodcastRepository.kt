package com.matthieu.zi_app.data.repository

import android.content.Context
import com.matthieu.zi_app.data.local.dao.PodcastDao
import com.matthieu.zi_app.data.local.dao.VocabularyDao
import com.matthieu.zi_app.data.local.entity.PodcastChannelEntity
import com.matthieu.zi_app.data.local.entity.PodcastEpisodeEntity
import com.matthieu.zi_app.data.local.entity.PodcastSentenceEntity
import com.matthieu.zi_app.data.local.entity.PodcastVocabEntity
import com.matthieu.zi_app.data.podcast.PodcastRssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class PodcastRepository(
    private val podcastDao: PodcastDao,
    private val context: Context,
    private val dictionaryDao: VocabularyDao? = null
) {

    // Channels
    fun getAllChannels(): Flow<List<PodcastChannelEntity>> = podcastDao.getAllChannels()

    fun getEnabledChannels(): Flow<List<PodcastChannelEntity>> = podcastDao.getEnabledChannels()

    suspend fun getChannelById(channelId: Int): PodcastChannelEntity? =
        podcastDao.getChannelById(channelId)

    suspend fun updateChannel(channel: PodcastChannelEntity) =
        podcastDao.updateChannel(channel)

    // Episodes
    fun getAllEpisodes(): Flow<List<PodcastEpisodeEntity>> = podcastDao.getAllEpisodes()

    suspend fun getAllEpisodesSync(): List<PodcastEpisodeEntity> =
        podcastDao.getAllEpisodesSync()

    fun getEpisodesForChannel(channelId: Int): Flow<List<PodcastEpisodeEntity>> =
        podcastDao.getEpisodesForChannel(channelId)

    suspend fun getEpisodesForChannelSync(channelId: Int): List<PodcastEpisodeEntity> =
        podcastDao.getEpisodesForChannelSync(channelId)

    suspend fun getEpisodeCountForChannel(channelId: Int): Int =
        podcastDao.getEpisodeCountForChannel(channelId)

    suspend fun getEpisodeById(episodeId: Int): PodcastEpisodeEntity? =
        podcastDao.getEpisodeById(episodeId)

    fun getEpisodeByIdFlow(episodeId: Int): Flow<PodcastEpisodeEntity?> =
        podcastDao.getEpisodeByIdFlow(episodeId)

    fun searchEpisodes(query: String): Flow<List<PodcastEpisodeEntity>> =
        podcastDao.searchEpisodes(query)

    fun getDownloadedEpisodes(): Flow<List<PodcastEpisodeEntity>> =
        podcastDao.getDownloadedEpisodes()

    suspend fun updatePlaybackPosition(episodeId: Int, positionMs: Int, isCompleted: Boolean) =
        podcastDao.updatePlaybackPosition(episodeId, positionMs, isCompleted)

    // Sentences & Vocab
    fun getSentencesForEpisode(episodeId: Int): Flow<List<PodcastSentenceEntity>> =
        podcastDao.getSentencesForEpisode(episodeId)

    suspend fun getSentencesForEpisodeSync(episodeId: Int): List<PodcastSentenceEntity> =
        podcastDao.getSentencesForEpisodeSync(episodeId)

    fun getVocabForEpisode(episodeId: Int): Flow<List<PodcastVocabEntity>> =
        podcastDao.getVocabForEpisode(episodeId)

    suspend fun getVocabForEpisodeSync(episodeId: Int): List<PodcastVocabEntity> =
        podcastDao.getVocabForEpisodeSync(episodeId)

    // Dynamic RSS Ingestion
    suspend fun syncFromInputStream(channelId: Int, inputStream: InputStream): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val parsedEpisodes = PodcastRssParser.parseRss(inputStream, defaultChannelId = channelId)
            if (parsedEpisodes.isEmpty()) {
                return@withContext Result.success(0)
            }

            val existingEpisodes = podcastDao.getAllEpisodesSync()
            val existingByAudioUrl = existingEpisodes.filter { it.audioUrl.isNotBlank() }
                .associateBy { it.audioUrl }
            val existingByNum = existingEpisodes.filter { it.episodeNum > 0 }
                .associateBy { "${it.channelId}_${it.episodeNum}" }
            val existingByTitle = existingEpisodes.associateBy { "${it.channelId}_${it.title}" }

            val episodesToSave = parsedEpisodes.map { parsed ->
                val match = existingByAudioUrl[parsed.audioUrl]
                    ?: existingByNum["${parsed.channelId}_${parsed.episodeNum}"]
                    ?: existingByTitle["${parsed.channelId}_${parsed.title}"]

                if (match != null) {
                    parsed.copy(
                        episodeId = match.episodeId,
                        playbackPositionMs = match.playbackPositionMs,
                        isCompleted = match.isCompleted,
                        downloadedPath = match.downloadedPath
                    )
                } else {
                    parsed.copy(episodeId = 0)
                }
            }

            podcastDao.insertEpisodes(episodesToSave)
            Result.success(episodesToSave.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncChannelEpisodes(channelId: Int): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val channel = podcastDao.getChannelById(channelId)
                ?: return@withContext Result.failure(IllegalArgumentException("Channel $channelId not found"))
            val feedUrl = channel.feedUrl
            if (feedUrl.isNullOrBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Feed URL for channel $channelId is blank"))
            }

            var currentUrl = feedUrl
            var connection: HttpURLConnection
            var redirects = 0

            while (true) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (AndZi Android)")
                }
                val code = connection.responseCode
                if (code in 300..399 && redirects < 5) {
                    val location = connection.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        currentUrl = location
                        redirects++
                        continue
                    }
                }
                if (code !in 200..299) {
                    return@withContext Result.failure(Exception("HTTP error $code: ${connection.responseMessage}"))
                }
                break
            }

            android.util.Log.d("PodcastRepository", "Syncing channel $channelId from $currentUrl")
            val res = connection.inputStream.use { stream ->
                syncFromInputStream(channelId, stream)
            }
            android.util.Log.d("PodcastRepository", "Sync result for channel $channelId: $res")
            res
        } catch (e: Exception) {
            android.util.Log.e("PodcastRepository", "Sync failed for channel $channelId", e)
            Result.failure(e)
        }
    }

    // Dynamic CC-CEDICT Vocabulary Segmentation
    suspend fun extractAndSaveVocabForEpisode(episodeId: Int, transcript: String): List<PodcastVocabEntity> = withContext(Dispatchers.IO) {
        val existing = podcastDao.getVocabForEpisodeSync(episodeId)
        if (existing.isNotEmpty()) {
            return@withContext existing
        }

        val dao = dictionaryDao ?: return@withContext emptyList()
        if (transcript.isBlank()) {
            return@withContext emptyList()
        }

        try {
            // 1. Split transcript into Chinese character blocks
            val chineseBlocks = transcript.split(Regex("[^\\u4E00-\\u9FA5]+"))
                .filter { it.length >= 2 }

            if (chineseBlocks.isEmpty()) {
                return@withContext emptyList()
            }

            // 2. Collect candidate substrings (length 2 to 4)
            val candidateSet = mutableSetOf<String>()
            for (block in chineseBlocks) {
                for (i in block.indices) {
                    val maxLen = minOf(4, block.length - i)
                    for (len in 2..maxLen) {
                        candidateSet.add(block.substring(i, i + len))
                    }
                }
            }

            if (candidateSet.isEmpty()) {
                return@withContext emptyList()
            }

            // 3. Query CC-CEDICT in chunks of 900
            val candidatesList = candidateSet.toList()
            val matchedWords = candidatesList.chunked(900).flatMap { chunk ->
                dao.getWordsForChineseList(chunk)
            }

            if (matchedWords.isEmpty()) {
                return@withContext emptyList()
            }

            val validChineseSet = matchedWords.map { it.chinese }.toSet()
            val wordsByChinese = matchedWords.groupBy { it.chinese }

            // Fetch translations
            val wordIds = matchedWords.map { it.cwordId.toLong() }
            val translations = wordIds.chunked(900).flatMap { chunk ->
                dao.getTranslationsForWordIds(chunk)
            }.groupBy { it.cwordId }

            // Fetch HSK levels
            val hskLevels = candidatesList.chunked(900).flatMap { chunk ->
                dao.getHskVocabForWords(chunk)
            }.associate { it.hanzi to it.hskLevel }

            // 4. Forward Maximum Matching (FMM) to preserve occurrence order
            val orderedWords = mutableListOf<String>()
            val seenWords = mutableSetOf<String>()

            for (block in chineseBlocks) {
                var i = 0
                while (i < block.length) {
                    var matchedLen = 0
                    val maxLen = minOf(4, block.length - i)
                    for (len in maxLen downTo 2) {
                        val candidate = block.substring(i, i + len)
                        if (candidate in validChineseSet) {
                            matchedLen = len
                            if (seenWords.add(candidate)) {
                                orderedWords.add(candidate)
                            }
                            break
                        }
                    }
                    if (matchedLen > 0) {
                        i += matchedLen
                    } else {
                        i += 1
                    }
                }
            }

            // 5. Build and insert PodcastVocabEntity items
            val vocabEntities = orderedWords.mapNotNull { chinese ->
                val wordEntity = wordsByChinese[chinese]?.firstOrNull() ?: return@mapNotNull null
                val transList = translations[wordEntity.cwordId.toLong()]
                    ?.map { it.translation }
                    ?.take(3)
                    ?: emptyList()
                val formattedTrans = transList.joinToString("; ")
                val hsk = hskLevels[chinese] ?: 0

                PodcastVocabEntity(
                    episodeId = episodeId,
                    chinese = chinese,
                    pinyin = wordEntity.pinyin,
                    translation = formattedTrans,
                    hskLevel = hsk
                )
            }

            if (vocabEntities.isNotEmpty()) {
                podcastDao.insertVocab(vocabEntities)
            }

            vocabEntities
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // Audio file and cache helpers
    private fun getAudioDir(): File {
        val dir = File(context.filesDir, "podcasts")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getPlayableAudioSource(episode: PodcastEpisodeEntity): String {
        val localPath = episode.downloadedPath
        if (localPath != null) {
            val file = File(localPath)
            if (file.exists() && file.length() > 0) {
                return file.absolutePath
            }
        }
        return episode.audioUrl
    }

    suspend fun downloadEpisodeAudio(
        episode: PodcastEpisodeEntity,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val channelDir = File(getAudioDir(), episode.channelId.toString())
            if (!channelDir.exists()) {
                channelDir.mkdirs()
            }
            val targetFile = File(channelDir, "${episode.episodeId}.mp3")
            val tempFile = File(channelDir, "${episode.episodeId}.tmp")

            val url = URL(episode.audioUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.connect()

            if (connection.responseCode !in 200..299) {
                return@withContext Result.failure(Exception("HTTP Error ${connection.responseCode}"))
            }

            val totalBytes = connection.contentLengthLong
            var downloadedBytes: Long = 0

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            onProgress(downloadedBytes.toFloat() / totalBytes)
                        }
                    }
                    output.flush()
                }
            }

            if (tempFile.renameTo(targetFile)) {
                podcastDao.updateDownloadedPath(episode.episodeId, targetFile.absolutePath)
                Result.success(targetFile)
            } else {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
                podcastDao.updateDownloadedPath(episode.episodeId, targetFile.absolutePath)
                Result.success(targetFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun deleteDownloadedAudio(episodeId: Int): Boolean = withContext(Dispatchers.IO) {
        val episode = podcastDao.getEpisodeById(episodeId) ?: return@withContext false
        val path = episode.downloadedPath ?: return@withContext false
        val file = File(path)
        if (file.exists()) {
            file.delete()
        }
        podcastDao.updateDownloadedPath(episodeId, null)
        true
    }

    suspend fun getAudioCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        var size: Long = 0
        val audioDir = getAudioDir()
        if (audioDir.exists()) {
            audioDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    size += file.length()
                }
            }
        }
        size
    }

    suspend fun clearAudioCache(): Boolean = withContext(Dispatchers.IO) {
        val audioDir = getAudioDir()
        if (audioDir.exists()) {
            audioDir.deleteRecursively()
            audioDir.mkdirs()
        }
        podcastDao.clearAllDownloadedPaths()
        true
    }
}
