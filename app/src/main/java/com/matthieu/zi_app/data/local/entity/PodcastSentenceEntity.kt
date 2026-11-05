package com.matthieu.zi_app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "podcast_sentence",
    foreignKeys = [
        ForeignKey(
            entity = PodcastEpisodeEntity::class,
            parentColumns = ["episode_id"],
            childColumns = ["episode_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["episode_id"], name = "idx_sentence_episode")
    ]
)
data class PodcastSentenceEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "sentence_id")
    val sentenceId: Int = 0,
    @ColumnInfo(name = "episode_id")
    val episodeId: Int,
    @ColumnInfo(name = "sentence_idx")
    val sentenceIdx: Int,
    @ColumnInfo(name = "chinese_text")
    val chineseText: String,
    @ColumnInfo(name = "english_text")
    val englishText: String? = null,
    @ColumnInfo(name = "start_ms")
    val startMs: Int = 0,
    @ColumnInfo(name = "end_ms")
    val endMs: Int = 0
)

