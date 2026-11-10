package com.matthieu.zi_app.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.matthieu.zi_app.data.local.entity.PodcastChannelEntity
import com.matthieu.zi_app.data.local.entity.PodcastEpisodeEntity
import com.matthieu.zi_app.data.local.entity.PodcastSentenceEntity
import com.matthieu.zi_app.data.local.entity.PodcastVocabEntity
import kotlinx.coroutines.flow.Flow

data class EpisodeVocabWord(
    @ColumnInfo(name = "episode_id") val episodeId: Int,
    @ColumnInfo(name = "chinese") val chinese: String
)

@Dao
interface PodcastDao {

    // Channels
    @Query("SELECT * FROM podcast_channel ORDER BY channel_id ASC")
    fun getAllChannels(): Flow<List<PodcastChannelEntity>>

    @Query("SELECT * FROM podcast_channel WHERE is_enabled = 1 ORDER BY channel_id ASC")
    fun getEnabledChannels(): Flow<List<PodcastChannelEntity>>

    @Query("SELECT * FROM podcast_channel WHERE channel_id = :channelId LIMIT 1")
    suspend fun getChannelById(channelId: Int): PodcastChannelEntity?

    @Update
    suspend fun updateChannel(channel: PodcastChannelEntity)

    // Episodes
    @Query("SELECT * FROM podcast_episode ORDER BY channel_id ASC, episode_num ASC")
    fun getAllEpisodes(): Flow<List<PodcastEpisodeEntity>>

    @Query("SELECT * FROM podcast_episode ORDER BY channel_id ASC, episode_num ASC")
    suspend fun getAllEpisodesSync(): List<PodcastEpisodeEntity>

    @Query("SELECT * FROM podcast_episode WHERE channel_id = :channelId ORDER BY episode_num ASC")
    fun getEpisodesForChannel(channelId: Int): Flow<List<PodcastEpisodeEntity>>

    @Query("SELECT * FROM podcast_episode WHERE channel_id = :channelId ORDER BY episode_num ASC")
    suspend fun getEpisodesForChannelSync(channelId: Int): List<PodcastEpisodeEntity>

    @Query("SELECT COUNT(*) FROM podcast_episode WHERE channel_id = :channelId")
    suspend fun getEpisodeCountForChannel(channelId: Int): Int

    @Query("SELECT * FROM podcast_episode WHERE episode_id = :episodeId LIMIT 1")
    suspend fun getEpisodeById(episodeId: Int): PodcastEpisodeEntity?

    @Query("SELECT * FROM podcast_episode WHERE episode_id = :episodeId LIMIT 1")
    fun getEpisodeByIdFlow(episodeId: Int): Flow<PodcastEpisodeEntity?>

    @Query("""
        SELECT * FROM podcast_episode 
        WHERE title LIKE '%' || :query || '%' 
        ORDER BY episode_num ASC
    """)
    fun searchEpisodes(query: String): Flow<List<PodcastEpisodeEntity>>

    @Query("SELECT * FROM podcast_episode WHERE downloaded_path IS NOT NULL ORDER BY episode_num ASC")
    fun getDownloadedEpisodes(): Flow<List<PodcastEpisodeEntity>>

    @Update
    suspend fun updateEpisode(episode: PodcastEpisodeEntity)

    @Query("""
        UPDATE podcast_episode 
        SET playback_position_ms = :positionMs, is_completed = :isCompleted 
        WHERE episode_id = :episodeId
    """)
    suspend fun updatePlaybackPosition(episodeId: Int, positionMs: Int, isCompleted: Boolean)

    @Query("UPDATE podcast_episode SET downloaded_path = :path WHERE episode_id = :episodeId")
    suspend fun updateDownloadedPath(episodeId: Int, path: String?)

    @Query("UPDATE podcast_episode SET target_hsk_level = :hskLevel WHERE episode_id = :episodeId")
    suspend fun updateTargetHskLevel(episodeId: Int, hskLevel: Int)

    @Query("UPDATE podcast_episode SET downloaded_path = NULL")
    suspend fun clearAllDownloadedPaths()

    // Sentences
    @Query("SELECT * FROM podcast_sentence WHERE episode_id = :episodeId ORDER BY sentence_idx ASC")
    fun getSentencesForEpisode(episodeId: Int): Flow<List<PodcastSentenceEntity>>

    @Query("SELECT * FROM podcast_sentence WHERE episode_id = :episodeId ORDER BY sentence_idx ASC")
    suspend fun getSentencesForEpisodeSync(episodeId: Int): List<PodcastSentenceEntity>

    // Vocabulary
    @Query("SELECT * FROM podcast_vocab WHERE episode_id = :episodeId ORDER BY vocab_id ASC")
    fun getVocabForEpisode(episodeId: Int): Flow<List<PodcastVocabEntity>>

    @Query("SELECT * FROM podcast_vocab WHERE episode_id = :episodeId ORDER BY vocab_id ASC")
    suspend fun getVocabForEpisodeSync(episodeId: Int): List<PodcastVocabEntity>

    @Query("SELECT episode_id, chinese FROM podcast_vocab")
    fun getAllVocabWordsFlow(): Flow<List<EpisodeVocabWord>>

    @Query("SELECT episode_id, chinese FROM podcast_vocab")
    suspend fun getAllVocabWordsSync(): List<EpisodeVocabWord>

    @Query("SELECT COUNT(*) FROM podcast_sentence ps INNER JOIN podcast_episode pe ON ps.episode_id = pe.episode_id WHERE pe.channel_id = :channelId")
    suspend fun getSentenceCountForChannel(channelId: Int): Int

    @Query("SELECT COUNT(*) FROM podcast_vocab pv INNER JOIN podcast_episode pe ON pv.episode_id = pe.episode_id WHERE pe.channel_id = :channelId")
    suspend fun getVocabCountForChannel(channelId: Int): Int

    // Inserts
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<PodcastChannelEntity>)

    @Query("DELETE FROM podcast_sentence WHERE episode_id = :episodeId")
    suspend fun deleteSentencesForEpisode(episodeId: Int)

    @Query("DELETE FROM podcast_sentence WHERE episode_id IN (SELECT episode_id FROM podcast_episode WHERE channel_id = :channelId)")
    suspend fun deleteSentencesForChannel(channelId: Int)

    @Query("DELETE FROM podcast_vocab WHERE episode_id = :episodeId")
    suspend fun deleteVocabForEpisode(episodeId: Int)

    @Query("DELETE FROM podcast_vocab WHERE episode_id IN (SELECT episode_id FROM podcast_episode WHERE channel_id = :channelId)")
    suspend fun deleteVocabForChannel(channelId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<PodcastEpisodeEntity>): List<Long>

    @Transaction
    suspend fun insertEpisodesInChunks(episodes: List<PodcastEpisodeEntity>, chunkSize: Int = 50) {
        episodes.chunked(chunkSize).forEach { chunk ->
            insertEpisodes(chunk)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSentences(sentences: List<PodcastSentenceEntity>)

    @Transaction
    suspend fun insertSentencesInChunks(sentences: List<PodcastSentenceEntity>, chunkSize: Int = 100) {
        sentences.chunked(chunkSize).forEach { chunk ->
            insertSentences(chunk)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVocab(vocab: List<PodcastVocabEntity>)

    @Transaction
    suspend fun insertVocabInChunks(vocab: List<PodcastVocabEntity>, chunkSize: Int = 150) {
        vocab.chunked(chunkSize).forEach { chunk ->
            insertVocab(chunk)
        }
    }
}
