package com.matthieu.zi_app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "podcast_episode",
    foreignKeys = [
        ForeignKey(
            entity = PodcastChannelEntity::class,
            parentColumns = ["channel_id"],
            childColumns = ["channel_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["channel_id"], name = "idx_episode_channel"),
        Index(value = ["channel_id", "episode_num"], name = "idx_episode_num")
    ]
)
data class PodcastEpisodeEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "episode_id")
    val episodeId: Int = 0,
    @ColumnInfo(name = "channel_id")
    val channelId: Int,
    @ColumnInfo(name = "episode_num")
    val episodeNum: Int,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "audio_url")
    val audioUrl: String,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Int = 0,
    @ColumnInfo(name = "transcript")
    val transcript: String,
    @ColumnInfo(name = "pub_date")
    val pubDate: Long = 0,
    @ColumnInfo(name = "playback_position_ms")
    val playbackPositionMs: Int = 0,
    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,
    @ColumnInfo(name = "downloaded_path")
    val downloadedPath: String? = null,
    @ColumnInfo(name = "target_hsk_level")
    val targetHskLevel: Int = 0
)

